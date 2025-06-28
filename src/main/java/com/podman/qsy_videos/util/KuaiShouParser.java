package com.podman.qsy_videos.util;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.podman.qsy_videos.common.Result;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class KuaiShouParser {

    private static final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();



    public static Result parseShareUrl(String shareUrl) throws Exception {
        String locationUrl = fetchLocationUrl(shareUrl);
        String redirectUrl = locationUrl;
        if (!shareUrl.contains("v.kuaishou.com")) {
            redirectUrl = fetchRedirectUrl(locationUrl);
        }
        String htmlContent = fetchPageContent(redirectUrl);
        String jsonData = extractJsonData(htmlContent);
        JSONObject photoData = findPhotoData(jsonData);
        return buildVideoInfo(photoData);
    }

    private static String fetchLocationUrl(String shareUrl) throws Exception {
        String userAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 16_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(shareUrl))
                .header("User-Agent", userAgent)
                .header("Referer", "https://v.kuaishou.com/")
                .GET()
                .build();
        HttpResponse<String> response = sendRequestWithRetry(request, 3);
        return response.headers().firstValue("location")
                .orElseThrow(() -> new Exception("failed to get location from share URL"));
    }

    private static String fetchRedirectUrl(String locationUrl) throws Exception {
        String userAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 16_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(locationUrl.replace("/fw/long-video/", "/fw/photo/")))
                .header("User-Agent", userAgent)
                .header("Referer", "https://v.kuaishou.com/")
                .GET()
                .build();
        HttpResponse<String> response = sendRequestWithRetry(request, 3);

        Pattern pattern = Pattern.compile("<a\\s+href\\s*=\\s*\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(response.body());

        if (!matcher.find()) {
            throw new Exception("failed to extract href from redirect page");
        }
        return matcher.group(1);
    }

    private static String extractJsonData(String html) throws Exception {
        Pattern pattern = Pattern.compile("window\\.INIT_STATE\\s*=\\s*(.*?)</script>", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(html);
        if (!matcher.find()) {
            throw new Exception("failed to extract INIT_STATE JSON");
        }

        return matcher.group(1).trim();
    }

    private static JSONObject findPhotoData(String jsonText) throws Exception {
        Map<String, Object> jsonData = JSON.parseObject(jsonText, Map.class);
        for (Object value : jsonData.values()) {
            if (value instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) value;
                if (map.containsKey("result") && map.containsKey("photo")) {
                    JSONObject result = (JSONObject) value;
                    int resultCode = result.getIntValue("result");
                    if (resultCode != 1) {
                        throw new Exception("获取作品信息失败: result=" + resultCode);
                    }
                    return result;
                }
            }
        }

        throw new Exception("failed to parse photo info from INIT_STATE");
    }

    private static String fetchPageContent(String url) throws Exception {
        String userAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 16_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", userAgent)
                .header("Referer", "https://v.kuaishou.com/")
                .GET()
                .build();

        HttpResponse<String> response = sendRequestWithRetry(request, 3);
        return response.body();
    }

    private static Result buildVideoInfo(JSONObject photoData) {
        JSONObject data = photoData.getJSONObject("photo");

        String videoUrl = "";
        JSONArray mainMvUrls = data.getJSONArray("mainMvUrls");
        if (mainMvUrls != null && !mainMvUrls.isEmpty()) {
            videoUrl = mainMvUrls.getJSONObject(0).getString("url");
        }

        List<String> images = new ArrayList<>();
        JSONObject extParams = data.getJSONObject("ext_params");
        if (extParams != null) {
            JSONObject atlas = extParams.getJSONObject("atlas");
            if (atlas != null) {
                JSONArray cdnList = atlas.getJSONArray("cdn");
                JSONArray atlasList = atlas.getJSONArray("list");
                if (cdnList != null && !cdnList.isEmpty() && atlasList != null && !atlasList.isEmpty()) {
                    String cdn = cdnList.getString(0);
                    for (int i = 0; i < atlasList.size(); i++) {
                        images.add("https://" + cdn + "/" + atlasList.getString(i));
                    }
                }
            }
        }
        JSONArray coverUrls = data.getJSONArray("coverUrls");
        String coverUrl = "";
        if (coverUrls!=null && !coverUrls.isEmpty()) {
            JSONObject coverUrlObj = coverUrls.getJSONObject(0);
            coverUrl = coverUrlObj.getString("url");
        }

        Result result = new Result();
        result.setData(Result.DataObj.builder()
                        .video_title(data.getString("caption"))
                        .video_url(videoUrl)
                        .download_url(videoUrl)
                        .image_url(data.getString("headUrl"))//header url
                        .uid(data.getString("userEid"))
                        .avatar_url(coverUrl)
                        .name(data.getString("userName"))
                        .images(images)
                        .platform("kuaishou")
                .build());
        return result;
    }


    private static HttpResponse<String> sendRequestWithRetry(HttpRequest request, int maxRetries) throws Exception {
        int attempt = 0;
        while (attempt++ < maxRetries) {
            try {
                return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (IOException e) {
                if (attempt >= maxRetries) throw e;
                Thread.sleep(1000); // 等待一秒后重试
            }
        }
        throw new Exception("HTTP 请求失败，已达最大重试次数");
    }

}

package com.podman.qsy_videos.provider.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.podman.qsy_videos.common.Result;
import com.podman.qsy_videos.provider.ParseVideo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
public class XhsParseImpl implements ParseVideo {

    private static final HttpClient httpClient = HttpClient.newHttpClient();
    private static final String USER_AGENT = "Mozilla/5.0 (iPhone; CPU iPhone OS 16_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1 Edg/122.0.0.0";

    @Override
    public Result parse(String url) {
        return parseXhsVideo(url);
    }

    @Override
    public boolean support(String url) {
        //http://xhslink.com/m/1EtjbUlstzt
        return url.contains(".xiaohongshu.com") || url.contains("xhs");
    }

    public static Result parseXhsVideo(String url) {
        if (url == null || url.isEmpty()) {
            throw new IllegalArgumentException("URl 参数不能为空！");
        }
        return xhs(url);
    }

    private static Result xhs(String url) {
        try {
            if (url.contains("xhslink.com")) {//暂时只支持 手机版本的
                url = getRedirectUrl(url);
            }
            String response = sendGetRequest(url);
            // 使用正则表达式提取JSON数据
            Pattern pattern = Pattern.compile("<script>\\s*window.__INITIAL_STATE__\\s*=\\s*(\\{[\\s\\S]*?})</script>");
            assert response != null;
            Matcher matcher = pattern.matcher(response);
            if (matcher.find()) {
                String jsonData = matcher.group(1).replace("undefined", "null");

                // 使用Fastjson2解析JSON
                JSONObject decoded = JSON.parseObject(jsonData);
                JSONObject noteData = getNestedJsonObject(decoded, "noteData", "data", "noteData");

                if (noteData != null) {
                    JSONObject video = getNestedJsonObject(noteData, "video", "media", "stream");
                    String videoUrl = null;
                    if (video != null && video.containsKey("h265")) {
                        List<JSONObject> h265 = video.getJSONArray("h265").toList(JSONObject.class);
                        if (!h265.isEmpty()) {
                            videoUrl = h265.get(0).getString("masterUrl");
                        }
                    }

                    if (videoUrl != null && !videoUrl.isEmpty()) {
                        JSONObject user = noteData.getJSONObject("user");
                        Map<String, Object> data = new HashMap<>();
                        data.put("author", user != null ? user.getString("nickName") : "");
                        data.put("authorID", user != null ? user.getString("userId") : "");
                        data.put("title", noteData.getString("title"));
                        data.put("desc", noteData.getString("desc"));
                        data.put("avatar", user != null ? user.getString("avatar") : "");

                        List<JSONObject> imageList = noteData.getJSONArray("imageList").toList(JSONObject.class);
                        String cover = "";
                        if (!imageList.isEmpty()) {
                            cover = imageList.get(0).getString("url");
                        }

                        data.put("cover", cover);
                        data.put("url", videoUrl);

                        Result result = new Result();
                        Result.DataObj dataEntity = Result.DataObj.builder()
                                .video_title(noteData.getString("title"))
                                .video_url(videoUrl)
                                .download_url(videoUrl)
                                .image_url(cover)
                                .uid(user != null ? user.getString("userId") : "")
                                .name(user != null ? user.getString("nickName") : "")
                                .avatar_url(user != null ? user.getString("avatar") : "")
                                .images(imageList.stream().map(img -> img.getString("url")).toList())
                                .platform("XHS")
                                .build();
                        result.setData(dataEntity);
                        result.setSuccess(true);
                        return result;
                    } else {
                        return getBuild();
                    }
                } else {
                    return getBuild();
                }
            } else {
                return getBuild();
            }
        } catch (Exception e) {
            return getBuild();
        }
    }

    private static Result getBuild() {
        return Result.builder()
                .success(false)
                .data(null)
                .build();
    }

    private static JSONObject getNestedJsonObject(JSONObject json, String... keys) {
        JSONObject current = json;
        for (String key : keys) {
            if (current == null || !current.containsKey(key)) {
                return null;
            }
            current = current.getJSONObject(key);
        }
        return current;
    }

    private static String sendGetRequest(String url) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.body();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static String getRedirectUrl(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 16_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1 Edg/122.0.0.0")
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return response.headers().firstValue("Location").orElse(url);
    }

}

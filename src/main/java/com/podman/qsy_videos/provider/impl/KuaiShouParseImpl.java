package com.podman.qsy_videos.provider.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.podman.qsy_videos.common.Result;
import com.podman.qsy_videos.provider.ParseVideo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
public class KuaiShouParseImpl implements ParseVideo {

    private static final HttpClient httpClient = HttpClient.newHttpClient();

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36 Edg/135.0.0.0";

    @Override
    public Result parse(String url) {
        try {
            Result.DataObj ks = ks(url);
            // 构建返回数据
            return Result.builder()
                    .success(true)
                    .data(ks)
                    .build();

        } catch (Exception e) {
            log.error("快手解析失败: {}", e.getMessage(), e);
            return buildErrorResult();
        }
    }

    @Override
    public boolean support(String url) {
        return StringUtils.hasText(url) && url.contains(".kuaishou.com");
    }


    // 构建错误响应
    private Result buildErrorResult() {
        return Result.builder()
                .success(false)
                .data(null)
                .build();
    }

    private static Result.DataObj ks(String url) throws IOException, InterruptedException {
        String newUrl = getRedirectedUrl(url);
        String response = "";
        Pattern shortVideoPattern = Pattern.compile("short-video/([^?]+)");
        Pattern photoPattern = Pattern.compile("photo/([^?]+)");

        Matcher shortVideoMatcher = shortVideoPattern.matcher(newUrl);
        Matcher photoMatcher = photoPattern.matcher(newUrl);

        String id = null;
        if (shortVideoMatcher.find()) {
            id = shortVideoMatcher.group(1);
            response = sendGetRequest(url);
            while (response == null) {
                response = sendGetRequest(url);
            }
        } else if (photoMatcher.find()) {
            id = photoMatcher.group(1);
            response = sendGetRequest("https://www.kuaishou.com/short-video/" + id);
            while (response == null) {
                response = sendGetRequest("https://www.kuaishou.com/short-video/" + id);
            }
        }

        if (id != null) {
            Pattern apolloStatePattern = Pattern.compile("window\\.__APOLLO_STATE__\\s*=\\s*(.*?)</script>", Pattern.DOTALL);
            Matcher apolloStateMatcher = apolloStatePattern.matcher(response);
            if (apolloStateMatcher.find()) {
                String apolloState = apolloStateMatcher.group(1);
                // 清理JSON数据
                String cleanedApolloState = apolloState.replaceAll("function\\s*\\([^)]*\\)\\s*\\{[^}]*\\}", ":")
                        .replaceAll(",\\s*(?=}|])", "")
                        .replace(";(:());", "");

                JSONObject apolloStateJson = JSON.parseObject(cleanedApolloState);
                JSONObject videoInfo = apolloStateJson.getJSONObject("defaultClient");
                if (videoInfo != null) {
                    String key = "VisionVideoDetailPhoto:" + id;
                    JSONObject json = videoInfo.getJSONObject(key);
                    if (json != null) {
                        String videoUrl = json.getString("photoUrl");
                        if (videoUrl != null && !videoUrl.isEmpty()) {
                            List<String> images = new ArrayList<>();
                            images.add(json.getString("coverUrl"));

                            return Result.DataObj.builder()
                                    .video_title(json.getString("caption"))
                                    .video_url(videoUrl)
                                    .download_url(videoUrl) // 同video_url
                                    .image_url(json.getString("coverUrl"))
                                    .uid(json.getJSONObject("author").getString("id"))
                                    .name(json.getJSONObject("author").getString("name"))
                                    .avatar_url(json.getJSONObject("author").getString("avatar"))
                                    .images(images)
                                    .platform("kuaishou")
                                    .build();
                        }
                    }
                }
            }
        }
        throw new IllegalStateException("id解析失败");
    }

    private static String getRedirectedUrl(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .method("HEAD", HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        return response.uri().toString();
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

}

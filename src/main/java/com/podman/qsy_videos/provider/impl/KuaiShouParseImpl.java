package com.podman.qsy_videos.provider.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONException;
import com.alibaba.fastjson2.JSONObject;
import com.podman.qsy_videos.common.Result;
import com.podman.qsy_videos.provider.ParseVideo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
public class KuaiShouParseImpl implements ParseVideo {

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36 Edg/135.0.0.0";

    @Override
    public Result parse(String url) {
        try {
            // 获取重定向后的 URL
            String newUrl = getRedirectedUrl(url);

            // 提取 ID
            String id = extractId(newUrl);
            if (id == null || id.isEmpty()) {
                return buildErrorResult("无法提取视频 ID");
            }

            // 构造请求 URL
            String requestUrl;
            if (newUrl.contains("/short-video/")) {
                requestUrl = "https://www.kuaishou.com/short-video/" + id;
            } else if (newUrl.contains("/photo/")) {
                requestUrl = "https://www.kuaishou.com/short-video/" + id;
            } else {
                return buildErrorResult("不支持的链接格式");
            }

            // 发起请求获取页面内容
            String response = curl(requestUrl, List.of(
                    "User-Agent: " + USER_AGENT
            ));

            if (response == null) {
                return buildErrorResult("请求失败");
            }

            // 使用正则提取 JSON 数据
            Pattern pattern = Pattern.compile("window\\.__APOLLO_STATE__\\s*=\\s*([^<]+)");
            Matcher matcher = pattern.matcher(response);

            if (!matcher.find()) {
                return buildErrorResult("JSON 解析失败");
            }

            String jsonDataStr = matcher.group(1).trim();

            // 清理非法函数表达式（PHP 中的 preg_replace 替代）
            jsonDataStr = jsonDataStr.replaceAll("function\\s*$$[^)]*$$\\s*\\{[^}]*\\}", ":");
            jsonDataStr = jsonDataStr.replaceAll(",\\s*(?=}|])", "");
            jsonDataStr = jsonDataStr.replace(";(:());", "");

            JSONObject apolloState;
            try {
                apolloState = JSON.parseObject(jsonDataStr);
            } catch (JSONException e) {
                return buildErrorResult("JSON 格式错误：" + e.getMessage());
            }

            JSONObject videoInfo = apolloState.getJSONObject("defaultClient");
            if (videoInfo == null) {
                return buildErrorResult("未找到 defaultClient 字段");
            }

            String key = "VisionVideoDetailPhoto:" + id;
            JSONObject json = videoInfo.getJSONObject(key);
            if (json == null) {
                return buildErrorResult("未找到 VisionVideoDetailPhoto 对象");
            }

            String videoUrl = json.getString("photoUrl");
            if (videoUrl == null || videoUrl.isEmpty()) {
                return buildErrorResult("视频地址为空");
            }

            // 构建返回数据
            return Result.builder()
                    .success(true)
                    .data(Result.DataObj.builder()
                            .video_title(json.getString("caption"))
                            .video_url(videoUrl)
                            .download_url(videoUrl)
                            .image_url(json.getString("coverUrl"))
                            .name("") // 快手未提供用户名
                            .uid("")
                            .avatar_url("")
                            .build())
                    .build();

        } catch (Exception e) {
            log.error("快手解析失败: {}", e.getMessage(), e);
            return buildErrorResult("快手解析失败");
        }
    }

    @Override
    public boolean support(String url) {
        return StringUtils.hasText(url) && url.contains(".kuaishou.com");
    }

    // 获取重定向后的 URL
    private String getRedirectedUrl(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setInstanceFollowRedirects(false);
        conn.setRequestMethod("HEAD");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.connect();

        String location = conn.getHeaderField("Location");
        return location != null ? location : url;
    }

    // 提取视频 ID
    private String extractId(String url) {
        Pattern shortVideoPattern = Pattern.compile("/short-video/([^?]+)");
        Matcher m = shortVideoPattern.matcher(url);
        if (m.find()) {
            return m.group(1);
        }

        Pattern photoPattern = Pattern.compile("/photo/([^?]+)");
        m = photoPattern.matcher(url);
        if (m.find()) {
            return m.group(1);
        }

        return null;
    }

    // 模拟 curl 请求
    private String curl(String url, List<String> headers) {
        StringBuilder response = new StringBuilder();
        try {
            URL obj = new URL(url);
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();

            con.setRequestMethod("GET");
            con.setConnectTimeout(5000);
            con.setReadTimeout(5000);
            con.setInstanceFollowRedirects(true);

            if (headers != null) {
                for (String header : headers) {
                    String[] parts = header.split(": ");
                    if (parts.length == 2) {
                        con.setRequestProperty(parts[0], parts[1]);
                    }
                }
            }

            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
            }
        } catch (IOException e) {
            return null;
        }
        return response.toString();
    }

    // 构建错误响应
    private Result buildErrorResult(String message) {
        return Result.builder()
                .success(false)
                .data(null)
                .build();
    }

}

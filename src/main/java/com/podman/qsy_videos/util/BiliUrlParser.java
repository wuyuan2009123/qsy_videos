package com.podman.qsy_videos.util;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.podman.qsy_videos.common.Result;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class BiliUrlParser {
    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/94.0.4606.81 Safari/537.36";

    // 创建 HttpClient 实例（推荐复用）
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @SneakyThrows
    public static Result parseBi(String url){
        assert url!=null;
        url = cleanUrlParameters(url);
        URL parsedUrl = new URL(url);
        String host = parsedUrl.getHost();
        String bvid = "";
        if ("b23.tv".equals(host)) {
            String redirectUrl = getRedirectLocation(url);
            parsedUrl = new URL(redirectUrl);
            bvid = parsedUrl.getPath().replaceAll("/+$", "");
        } else if ("www.bilibili.com".equals(host) || "m.bilibili.com".equals(host)) {
            bvid = parsedUrl.getPath();
        } else {
            log.info("视频链接好像不太对");
            return Result.builder().data(null).success(false).build();
        }
        if (!bvid.contains("/video/")) {
            log.info("好像不是视频链接");
            return Result.builder().data(null).success(false).build();
        }
        bvid = bvid.replace("/video/", "");

        String json = sendGetRequest("https://api.bilibili.com/x/web-interface/view?bvid=" + bvid);
        JSONObject response = JSON.parseObject(json);

        JSONObject data = response.getJSONObject("data");
        JSONArray pages = data.getJSONArray("pages");

        if (response.getIntValue("code") == 0) {

            List<String> videoList = new ArrayList<>();
            for (int i = 0; i < pages.size(); i++) {
                JSONObject page = pages.getJSONObject(i);
                String cid = page.getString("cid");
                String apiUrl2 = "https://api.bilibili.com/x/player/playurl?otype=json&fnver=0&fnval=3" +
                        "&player=3&qn=112&bvid=" + bvid + "&cid=" + cid + "&platform=html5&high_quality=1";
                String json2 = sendGetRequest(apiUrl2);
                JSONObject playInfo = JSON.parseObject(json2);
                JSONObject playData = playInfo.getJSONObject("data");
                String videoUrl = playData.getJSONArray("durl").getJSONObject(0).getString("url");
                // 处理视频URL
                if (videoUrl.contains(".bilivideo.com/")) {
                    videoUrl = "https://upos-sz-mirrorhw.bilivideo.com/" + videoUrl.split(".bilivideo.com/")[1];
                    videoList.add(videoUrl);
                }
            }

            JSONObject owner = response.getJSONObject("data").getJSONObject("owner");
            Result result = new Result();
            Result.DataObj build = Result.DataObj.builder()
                    .video_title(response.getJSONObject("data").getString("title"))
                    .video_url(videoList.get(0))
                    .download_url(videoList.get(0))
                    .name(owner.getString("name"))
                    .uid(owner.getString("mid"))
                    .image_url(owner.getString("face"))
                    .avatar_url(response.getJSONObject("data").getString("pic"))
                    .build();
            result.setData(build);
            result.setSuccess(true);
            return result;
        }
        log.error("解析失败");
        return Result.builder().data(null).success(false).build();
    }


//    public static void main(String[] args) throws Exception {
//        String url = "https://www.bilibili.com/video/BV1HLKozcE8X";
//        Result result = parseBi(url);
//        log.info(JSON.toJSONString(result));
//    }

    /**
     * 发送 GET 请求并返回响应字符串
     */
    private static String sendGetRequest(String urlStr) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(urlStr))
                .header("User-Agent", USER_AGENT)
                .header("Content-Type", "application/json;charset=UTF-8")
                .GET()
                .timeout(Duration.ofSeconds(10))
                .build();
        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    /**
     * 获取重定向地址
     */
    private static String getRedirectLocation(String urlStr) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(urlStr))
                .method("HEAD", HttpRequest.BodyPublishers.noBody())
                .header("User-Agent", USER_AGENT)
                .timeout(Duration.ofSeconds(10))
                .build();
        HttpResponse<Void> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.discarding());
        return response.headers().firstValue("Location").orElseThrow(() -> new IOException("No Location header found"));
    }

    /**
     * 清理 URL 参数（保留路径和域名）
     */
    private static String cleanUrlParameters(String url) throws URISyntaxException {
        URI uri = new URI(url);
        String scheme = uri.getScheme() != null ? uri.getScheme() + "://" : "";
        String host = uri.getHost() != null ? uri.getHost() : "";
        int port = uri.getPort();
        String path = uri.getRawPath() != null ? uri.getRawPath() : "";
        String fragment = uri.getRawFragment() != null ? "#" + uri.getRawFragment() : "";
        if (port != -1) {
            host += ":" + port;
        }
        path = path.replaceAll("/+$", "");
        return scheme + host + path + fragment;
    }

    /**
     * 格式化秒数为 HH:mm:ss
     */
    private static String formatDuration(int seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, secs);
    }

}

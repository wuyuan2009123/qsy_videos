package com.podman.qsy_videos.provider.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.podman.qsy_videos.common.Result;
import com.podman.qsy_videos.provider.ParseVideo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
public class DyParseImpl implements ParseVideo {

    private static final HttpClient httpClient = HttpClient.newHttpClient();

    @Override
    public Result parse(String url) {
        return dy(url);
    }

    @Override
    public boolean support(String url) {
        return StringUtils.hasText(url) && url.contains(".douyin.com");
    }



    public Result dy(String shareUrl) {
            String videoId = "";
            try {
                if (shareUrl.startsWith("https://www.douyin.com/video/")) {
                    videoId = shareUrl.strip().split("/")[4].split("\\?")[0];
                } else {
                    // 处理短链接跳转
                    videoId = extractId(shareUrl);;
                }
                String reqUrl = "https://www.iesdouyin.com/share/video/" + videoId + "/";
                HttpResponse<String> htmlRes = sendGetRequest(reqUrl);
                String html = htmlRes.body();
                Pattern pattern = Pattern.compile("window\\._ROUTER_DATA\\s*=\\s*(.*?)</script>", Pattern.DOTALL);
                Matcher matcher = pattern.matcher(html);
                if (!matcher.find()) {
                    throw new RuntimeException("parse video json info from html fail");
                }
                String jsonData = matcher.group(1).trim();
                JSONObject json = JSON.parseObject(jsonData);
                String videoKey = null;
                if (json.getJSONObject("loaderData").containsKey("video_(id)/page")) {
                    videoKey = "video_(id)/page";
                } else if (json.getJSONObject("loaderData").containsKey("note_(id)/page")) {
                    videoKey = "note_(id)/page";
                }
                if (videoKey == null) {
                    throw new RuntimeException("failed to parse Videos or Photo Gallery info from json");
                }
                JSONObject loaderData = json.getJSONObject("loaderData");
                JSONObject pageData = loaderData.getJSONObject(videoKey);
                JSONObject videoInfoRes = pageData.getJSONObject("videoInfoRes");

                JSONArray itemList = videoInfoRes.getJSONArray("item_list");
                if (itemList.isEmpty()) {
                    JSONArray filterList = videoInfoRes.getJSONArray("filter_list");
                    if (!filterList.isEmpty()) {
                        throw new RuntimeException(filterList.getJSONObject(0).getString("detail_msg"));
                    } else {
                        throw new RuntimeException("failed to parse video info from HTML");
                    }
                }

                JSONObject item = itemList.getJSONObject(0);
                List<String> images = new ArrayList<>();
                if (item.containsKey("images") && item.get("images") instanceof JSONArray) {
                    JSONArray imageArray = item.getJSONArray("images");
                    for (int i = 0; i < imageArray.size(); i++) {
                        JSONObject img = imageArray.getJSONObject(i);
                        if (img.containsKey("url_list") && img.getJSONArray("url_list").size() > 0) {
                            images.add(img.getJSONArray("url_list").getString(0));
                        }
                    }
                }
                String videoUrl = "";
                if (!images.isEmpty()) {
                    videoUrl = ""; // 图集时不需要视频地址
                } else {
                    videoUrl = item.getJSONObject("video")
                            .getJSONObject("play_addr")
                            .getJSONArray("url_list")
                            .getString(0)
                            .replace("playwm", "play");
                }

                String videoMp4Url = "";
                if (!videoUrl.isEmpty()) {
                    videoMp4Url = getRedirectUrl(videoUrl);
                }

                Result result = new Result();
                JSONObject authorObj = item.getJSONObject("author");
                Result.DataObj.DataObjBuilder builder = Result.DataObj.builder()
                                .video_title(item.getString("desc"))
                                .video_url(videoMp4Url)
                                .download_url(videoMp4Url)
                                .image_url(item.getJSONObject("video")
                                .getJSONObject("cover")
                                .getJSONArray("url_list")
                                .getString(0))
                                .uid(authorObj.getString("sec_uid"))
                                .name(authorObj.getString("nickname"))
                                .avatar_url(authorObj.getJSONObject("avatar_thumb")
                                        .getJSONArray("url_list")
                                        .getString(0))
                                .platform("douyin")
                                .images(images);
                result.setData(builder.build());
                result.setSuccess(Boolean.TRUE);
                return result;
            } catch (Exception e) {
                log.error("抖音解析失败，e：{}",e.getMessage());
                return Result.builder()
                        .success(false)
                        .data(null)
                        .build();
            }
    }


    public String extractId(String url) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setRequestMethod("HEAD");
            conn.connect();

            String loc = conn.getHeaderField("Location");
            if (loc == null) loc = url;

            Pattern idPattern = Pattern.compile("[0-9]+|(?<=video/)[0-9]+");
            Matcher m = idPattern.matcher(loc);
            if (m.find()) {
                return m.group();
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String getRedirectUrl(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 16_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1 Edg/122.0.0.0")
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return response.headers().firstValue("Location").orElse(url);
    }

    private HttpResponse<String> sendGetRequest(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 16_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1 Edg/122.0.0.0")
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

}

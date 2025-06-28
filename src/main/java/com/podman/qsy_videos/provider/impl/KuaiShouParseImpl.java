package com.podman.qsy_videos.provider.impl;

import com.podman.qsy_videos.common.Result;
import com.podman.qsy_videos.provider.ParseVideo;
import com.podman.qsy_videos.util.KuaiShouParser;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.http.HttpClient;

@Component
@Slf4j
public class KuaiShouParseImpl implements ParseVideo {

    private static final HttpClient httpClient = HttpClient.newHttpClient();

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36 Edg/135.0.0.0";

    @Override
    @SneakyThrows
    public Result parse(String url) {
        return KuaiShouParser.parseShareUrl(url);
    }

    @Override
    public boolean support(String url) {
        return StringUtils.hasText(url) && url.contains(".kuaishou.com");
    }


}

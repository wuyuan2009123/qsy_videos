package com.podman.qsy_videos.provider.impl;

import com.podman.qsy_videos.common.Result;
import com.podman.qsy_videos.provider.ParseVideo;
import com.podman.qsy_videos.util.BiliUrlParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Slf4j
public class BiParseImpl implements ParseVideo {
    @Override
    public Result parse(String url) {
        return BiliUrlParser.parseBi(url);
    }

    @Override
    public boolean support(String url) {
        return StringUtils.hasText(url) && (url.contains(".bilibili.com") || url.contains("b23.tv"));
    }
}

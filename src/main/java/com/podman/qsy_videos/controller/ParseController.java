package com.podman.qsy_videos.controller;

import com.podman.qsy_videos.common.Result;
import com.podman.qsy_videos.provider.ParseVideo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class ParseController {

    private final ObjectProvider<ParseVideo> parseVideos;

    @GetMapping("/parse")
    public Result parse(@RequestParam("url") String url) {
        ParseVideo parseVideo = parseVideos.stream().filter(it -> it.support(url)).findFirst().orElseThrow();
        return parseVideo.parse(url);
    }

}

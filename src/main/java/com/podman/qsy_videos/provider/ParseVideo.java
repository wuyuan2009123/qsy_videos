package com.podman.qsy_videos.provider;

import com.podman.qsy_videos.common.Result;

public interface ParseVideo {

    Result parse(String url);

    boolean support(String url);

}

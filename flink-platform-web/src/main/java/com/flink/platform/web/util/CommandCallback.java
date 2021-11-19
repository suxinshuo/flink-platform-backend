package com.flink.platform.web.util;

import lombok.Data;

/** Command callback. */
@Data
public class CommandCallback {

    private final String stdMessage;

    private final String errMessage;
}
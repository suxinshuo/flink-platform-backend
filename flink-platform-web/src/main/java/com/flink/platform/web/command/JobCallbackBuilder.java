package com.flink.platform.web.command;

/**
 * TODO
 * build call back info
 *
 * @author tiny.wang
 */
public interface JobCallbackBuilder {

    /**
     * extract app id
     *
     * @param message job command message
     * @return application id
     */
    String extractApplicationId(String message);

    /**
     * extract job id
     *
     * @param message job command message
     * @return job id
     */
    String extractJobId(String message);
}
package com.flink.platform.web.command;

import com.flink.platform.common.enums.JobType;
import com.flink.platform.dao.entity.JobInfo;

/** Command builder. */
public interface CommandBuilder {

    /**
     * whether support.
     *
     * @param jobType job type
     * @return whether support
     */
    boolean isSupported(JobType jobType, String version);

    /**
     * build a command from JobInfo.
     *
     * @param jobInfo job info
     * @return shell command
     * @throws Exception IO exception
     */
    JobCommand buildCommand(JobInfo jobInfo) throws Exception;
}

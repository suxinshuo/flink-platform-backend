package com.flink.platform.web.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.flink.platform.common.enums.ExecutionStatus;
import com.flink.platform.common.enums.JobStatus;
import com.flink.platform.common.enums.JobType;
import com.flink.platform.common.exception.UnrecoverableException;
import com.flink.platform.common.util.JsonUtil;
import com.flink.platform.dao.entity.JobInfo;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.service.JobInfoService;
import com.flink.platform.dao.service.JobRunInfoService;
import com.flink.platform.web.command.CommandBuilder;
import com.flink.platform.web.command.CommandExecutor;
import com.flink.platform.web.command.JobCallback;
import com.flink.platform.web.command.JobCommand;
import com.flink.platform.web.command.flink.FlinkCommand;
import com.flink.platform.web.enums.SqlVar;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.flink.platform.common.enums.ExecutionStatus.CREATED;
import static java.util.stream.Collectors.toMap;

/** Process job service. */
@Slf4j
@Service
public class ProcessJobService {

    private final JobInfoService jobInfoService;

    private final JobRunInfoService jobRunInfoService;

    private final List<CommandBuilder> jobCommandBuilders;

    private final List<CommandExecutor> jobCommandExecutors;

    @Autowired
    public ProcessJobService(
            JobInfoService jobInfoService,
            JobRunInfoService jobRunInfoService,
            List<CommandBuilder> jobCommandBuilders,
            List<CommandExecutor> jobCommandExecutors) {
        this.jobInfoService = jobInfoService;
        this.jobRunInfoService = jobRunInfoService;
        this.jobCommandBuilders = jobCommandBuilders;
        this.jobCommandExecutors = jobCommandExecutors;
    }

    public JobRunInfo processJob(final long jobRunId) throws Exception {
        JobCommand jobCommand = null;
        JobRunInfo jobRunInfo = null;

        try {
            // step 1: get job info
            jobRunInfo =
                    jobRunInfoService.getOne(
                            new QueryWrapper<JobRunInfo>()
                                    .lambda()
                                    .eq(JobRunInfo::getId, jobRunId)
                                    .eq(JobRunInfo::getStatus, CREATED));
            if (jobRunInfo == null) {
                throw new UnrecoverableException(
                        String.format("The job run: %s is no longer exists.", jobRunId));
            }

            JobInfo jobInfo =
                    jobInfoService.getOne(
                            new QueryWrapper<JobInfo>()
                                    .lambda()
                                    .eq(JobInfo::getId, jobRunInfo.getJobId())
                                    .eq(JobInfo::getStatus, JobStatus.ONLINE));
            if (jobInfo == null) {
                throw new UnrecoverableException(
                        String.format(
                                "The job: %s is no longer exists or in delete status.",
                                jobRunInfo.getJobId()));
            }

            // step 2: replace variables in the sql statement
            Map<String, Object> variableMap =
                    Arrays.stream(SqlVar.values())
                            .filter(sqlVar -> sqlVar.type == SqlVar.VarType.VARIABLE)
                            .filter(sqlVar -> jobInfo.getSubject().contains(sqlVar.variable))
                            .map(
                                    sqlVar ->
                                            Pair.of(
                                                    sqlVar.variable,
                                                    sqlVar.valueProvider.apply(jobInfo)))
                            .collect(toMap(Pair::getLeft, Pair::getRight));
            MapUtils.emptyIfNull(jobInfo.getVariables())
                    .forEach(
                            (name, value) -> {
                                SqlVar sqlVar = SqlVar.matchPrefix(name);
                                variableMap.put(name, sqlVar.valueProvider.apply(value));
                            });
            // replace variable with actual value
            for (Map.Entry<String, Object> entry : variableMap.entrySet()) {
                String originSubject = jobInfo.getSubject();
                String distSubject =
                        originSubject.replace(entry.getKey(), entry.getValue().toString());
                jobInfo.setSubject(distSubject);
            }

            JobType jobType = jobInfo.getType();
            String version = jobInfo.getVersion();

            // step 3: build job command, create a SqlContext if needed
            jobCommand =
                    jobCommandBuilders.stream()
                            .filter(builder -> builder.isSupported(jobType, version))
                            .findFirst()
                            .orElseThrow(
                                    () ->
                                            new UnrecoverableException(
                                                    "No available job command builder"))
                            .buildCommand(jobRunInfo.getFlowRunId(), jobInfo);

            // step 4: submit job
            LocalDateTime submitTime = LocalDateTime.now();
            final JobCommand command = jobCommand;
            JobCallback callback =
                    jobCommandExecutors.stream()
                            .filter(executor -> executor.isSupported(command))
                            .findFirst()
                            .orElseThrow(
                                    () ->
                                            new UnrecoverableException(
                                                    "No available job command executor"))
                            .execCommand(command);

            // step 5: write job run info to db
            ExecutionStatus executionStatus = callback.getStatus();
            JobRunInfo newJobRun = new JobRunInfo();
            newJobRun.setId(jobRunInfo.getId());
            newJobRun.setSubject(jobInfo.getSubject());
            newJobRun.setStatus(executionStatus);
            newJobRun.setVariables(variableMap);
            newJobRun.setBackInfo(JsonUtil.toJsonString(callback));
            newJobRun.setSubmitTime(submitTime);
            if (executionStatus.isTerminalState()) {
                newJobRun.setStopTime(LocalDateTime.now());
            }
            jobRunInfoService.updateById(newJobRun);

            // step 6: print job command info
            log.info("Job run: {} submitted, time: {}", jobRunId, System.currentTimeMillis());

            return jobRunInfo;
        } finally {
            if (jobRunInfo != null
                    && jobRunInfo.getType() == JobType.FLINK_SQL
                    && jobCommand != null) {
                try {
                    FlinkCommand flinkCommand = (FlinkCommand) jobCommand;
                    if (flinkCommand.getMainArgs() != null) {
                        Files.deleteIfExists(Paths.get(flinkCommand.getMainArgs()));
                    }
                } catch (Exception e) {
                    log.warn("Delete sql context file failed", e);
                }
            }
        }
    }
}

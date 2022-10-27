package com.flink.platform.web.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.flink.platform.common.exception.UnrecoverableException;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.entity.Worker;
import com.flink.platform.dao.service.JobRunInfoService;
import com.flink.platform.dao.service.WorkerService;
import com.flink.platform.grpc.JobGrpcServiceGrpc;
import com.flink.platform.grpc.KillJobReply;
import com.flink.platform.grpc.KillJobRequest;
import com.flink.platform.web.command.CommandExecutor;
import com.flink.platform.web.grpc.JobProcessGrpcClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

import static com.flink.platform.common.enums.ExecutionStatus.KILLED;
import static com.flink.platform.common.enums.ExecutionStatus.getNonTerminals;

/** Kill job service. */
@Slf4j
@Service
public class KillJobService {

    private final JobProcessGrpcClient jobProcessGrpcClient;

    private final JobRunInfoService jobRunInfoService;

    private final List<CommandExecutor> jobCommandExecutors;

    private final WorkerService workerService;

    @Autowired
    public KillJobService(
            JobRunInfoService jobRunInfoService,
            List<CommandExecutor> jobCommandExecutors,
            JobProcessGrpcClient jobProcessGrpcClient,
            WorkerService workerService) {
        this.jobRunInfoService = jobRunInfoService;
        this.jobCommandExecutors = jobCommandExecutors;
        this.jobProcessGrpcClient = jobProcessGrpcClient;
        this.workerService = workerService;
    }

    public long killRemoteJob(JobRunInfo jobRun) {
        String host = jobRun.getHost();
        Worker worker =
                workerService.getOne(
                        new QueryWrapper<Worker>()
                                .lambda()
                                .eq(Worker::getIp, host)
                                .last("LIMIT 1"));

        JobGrpcServiceGrpc.JobGrpcServiceBlockingStub stub =
                jobProcessGrpcClient.grpcClient(worker);
        KillJobRequest request = KillJobRequest.newBuilder().setJobRunId(jobRun.getId()).build();
        KillJobReply killJobReply = stub.killJob(request);
        return killJobReply.getJobRunId();
    }

    public void killJob(final long jobRunId) {
        JobRunInfo jobRun =
                jobRunInfoService.getOne(
                        new QueryWrapper<JobRunInfo>()
                                .lambda()
                                .eq(JobRunInfo::getId, jobRunId)
                                .in(JobRunInfo::getStatus, getNonTerminals()));
        if (jobRun == null) {
            return;
        }

        // exec kill
        jobCommandExecutors.stream()
                .filter(executor -> executor.isSupported(jobRun.getType()))
                .findFirst()
                .orElseThrow(() -> new UnrecoverableException("No available job command executor"))
                .kill(jobRunId);

        // set status to KILLED
        JobRunInfo newJobRun = new JobRunInfo();
        newJobRun.setId(jobRun.getId());
        newJobRun.setStatus(KILLED);
        newJobRun.setStopTime(LocalDateTime.now());
        jobRunInfoService.updateById(newJobRun);

        log.info("Kill job run: {} finished, time: {}", jobRunId, System.currentTimeMillis());
    }
}
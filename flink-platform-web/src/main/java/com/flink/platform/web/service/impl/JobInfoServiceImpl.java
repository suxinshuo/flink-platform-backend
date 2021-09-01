package com.flink.platform.web.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.flink.platform.common.enums.JobStatusEnum;
import com.flink.platform.common.enums.ResponseStatus;
import com.flink.platform.common.exception.DefinitionException;
import com.flink.platform.web.entity.JobInfo;
import com.flink.platform.web.mapper.JobInfoMapper;
import com.flink.platform.web.service.IJobInfoService;
import com.flink.platform.web.service.JobInfoQuartzService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 * job config info 服务实现类
 * </p>
 *
 * @author shik
 * @since 2021-04-14
 */
@Service
@DS("master_platform")
public class JobInfoServiceImpl extends ServiceImpl<JobInfoMapper, JobInfo> implements IJobInfoService {

    @Resource
    public JobInfoQuartzService jobInfoQuartzService;

    @Transactional(rollbackFor = RuntimeException.class)
    @Override
    public boolean save(JobInfo jobInfo) {
        boolean save = super.save(jobInfo);
        if (!save) {
            throw new DefinitionException(ResponseStatus.SERVICE_ERROR);
        }
        return true;
//        return jobInfoQuartzService.addJobToQuartz(jobInfo);
    }

    @Transactional(rollbackFor = RuntimeException.class)
    @Override
    public boolean updateById(JobInfo jobInfo) {
        boolean update = super.updateById(jobInfo);
        if (!update) {
            throw new DefinitionException(ResponseStatus.SERVICE_ERROR);
        }
        // TODO 修改job detail
        return true;
    }

    @Transactional(rollbackFor = RuntimeException.class)
    @Override
    public boolean stopJob(JobInfo jobInfo) {
        jobInfo.setStatus(JobStatusEnum.STOPPED.getCode()).setUpdateTime(LocalDateTime.now());
        boolean update = super.updateById(jobInfo);
        if (!update) {
            throw new DefinitionException(ResponseStatus.SERVICE_ERROR);
        }
        // 移除job quartz
        this.jobInfoQuartzService.removeJob(jobInfo.getCode());
        return true;
    }

    @Transactional(rollbackFor = RuntimeException.class)
    @Override
    public boolean openJob(JobInfo jobInfo) {
        jobInfo.setStatus(JobStatusEnum.SCHEDULED.getCode()).setUpdateTime(LocalDateTime.now());
        boolean update = super.updateById(jobInfo);
        if (!update) {
            throw new DefinitionException(ResponseStatus.SERVICE_ERROR);
        }
        jobInfoQuartzService.removeJob(jobInfo.getCode());
        return jobInfoQuartzService.addJobToQuartz(jobInfo);
    }
}
# flink-platform-web

> This project is a job scheduling framework with center less structure, easy to scale up.
> We can customize the workflow DAG and schedule it.

## Architecture

![arch](docs/img/arch_overview.png)

- WebUI: Frontend files are in a separate project written in vue, please
  visit [flink-platform-frontend](https://github.com/itinycheng/flink-platform-frontend).
- Platform Instance: The instance for manage, configuration and scheduling workflow, easy to scale.
- HDFS: Used to store resource files, such as jar, udf, etc.
- Mysql: Holds all info about jobs, users, resources, schedules, etc. To keep the system simple, I
  plan to use mysql to guarantee fault-tolerance.

## Task Support

- Flink sql/jar, deployment mode: YARN-Per-Job(tested), Other(untested).
- Shell (underway).
- ClickHouse sql(tested).

- More: I don't have enough time to develop multi type task support, but implementing a new task
  type is easy, sometimes you can do it yourself or tell me your needs.

## Metadata

| Table Name      | Description                                                    |
|:----------------|:---------------------------------------------------------------|
| t_alert         | Alert configuration                                            |
| t_catalog_info  | Flink catalog configuration for FlinkSQL, modify in the future |
| t_job           | Job info                                                       |
| t_job_flow      | Job flow info, workflow definition                             |
| t_job_flow_run  | Job flow execution instance                                    |
| t_job_run       | Job execution instance                                         |
| t_resource      | Resources info, such as: jar, file, etc.                       |
| t_user          | login user info                                                |
| t_user_session  | login user session info.                                       |
| t_worker        | Worker node instance info.                                     |

Refer to: [create table statements](docs/sql/schema.sql)

## Build and Run

```bash
# clone the project
git clone git@github.com:itinycheng/flink-platform-backend.git

# enter the project directory
cd flink-platform-backend

# compile
mvn clean package -DskipTests
```

```bash
#!/bin/sh

# hadoop conf dir of the cluster where the job running on
export HADOOP_CONF_DIR=/data0/app/dw_hadoop/yarn-conf

# start project
nohup java -Xms4g -Xmx4g -jar -Dspring.profiles.active=prod flink-platform-web-0.0.1.jar >/dev/null 2>&1 &
```

## License

[Apache-2.0](LICENSE) license.

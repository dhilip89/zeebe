package org.camunda.tngp.client;

import org.camunda.tngp.client.cmd.CompleteAsyncTaskCmd;
import org.camunda.tngp.client.cmd.CreateAsyncTaskCmd;
import org.camunda.tngp.client.cmd.PollAndLockAsyncTasksCmd;

public interface AsyncTaskService
{
    CreateAsyncTaskCmd create();

    PollAndLockAsyncTasksCmd pollAndLock();

    CompleteAsyncTaskCmd complete();
}
package org.camunda.tngp.broker.wf.runtime.log.handler.bpmn;

import org.camunda.tngp.bpmn.graph.ProcessGraph;
import org.camunda.tngp.broker.log.LogEntryHandler;
import org.camunda.tngp.broker.log.LogWriters;
import org.camunda.tngp.broker.wf.runtime.log.bpmn.BpmnFlowElementEventReader;
import org.camunda.tngp.graph.bpmn.BpmnAspect;
import org.camunda.tngp.log.idgenerator.IdGenerator;

public interface BpmnFlowElementAspectHandler
{
    /**
     * @return see constants defined in {@link LogEntryHandler}
     */
    int handle(BpmnFlowElementEventReader flowElementEventReader, ProcessGraph process, LogWriters logWriters, IdGenerator idGenerator);

    BpmnAspect getHandledBpmnAspect();
}
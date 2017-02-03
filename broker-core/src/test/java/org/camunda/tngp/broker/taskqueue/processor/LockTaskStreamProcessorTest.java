package org.camunda.tngp.broker.taskqueue.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.protocol.clientapi.EventType.TASK_EVENT;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.broker.Constants;
import org.camunda.tngp.broker.logstreams.BrokerEventMetadata;
import org.camunda.tngp.broker.taskqueue.data.TaskEvent;
import org.camunda.tngp.broker.taskqueue.data.TaskEventType;
import org.camunda.tngp.broker.test.MockStreamProcessorController;
import org.camunda.tngp.broker.test.WrittenEvent;
import org.camunda.tngp.logstreams.log.LoggedEvent;
import org.camunda.tngp.util.time.ClockUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class LockTaskStreamProcessorTest
{
    private static final byte[] TASK_TYPE = "test-task".getBytes(StandardCharsets.UTF_8);
    private static final DirectBuffer TASK_TYPE_BUFFER = new UnsafeBuffer(TASK_TYPE);

    private static final byte[] ANOTHER_TASK_TYPE = "another-task".getBytes(StandardCharsets.UTF_8);
    private static final DirectBuffer ANOTHER_TASK_TYPE_BUFFER = new UnsafeBuffer(ANOTHER_TASK_TYPE);

    private TaskSubscription subscription;
    private TaskSubscription anotherSubscription;

    private LockTaskStreamProcessor streamProcessor;

    @Mock
    private LoggedEvent mockLoggedEvent;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Rule
    public MockStreamProcessorController<TaskEvent> mockController = new MockStreamProcessorController<>(TaskEvent.class, TASK_EVENT);

    @Before
    public void setup() throws InterruptedException, ExecutionException
    {
        MockitoAnnotations.initMocks(this);

        // fix the current time to calculate lock time
        ClockUtil.setCurrentTime(Instant.now());

        streamProcessor = new LockTaskStreamProcessor();

        subscription = new TaskSubscription()
                .setId(1L)
                .setChannelId(11)
                .setTaskType(TASK_TYPE_BUFFER)
                .setLockDuration(Duration.ofMinutes(5).toMillis())
                .setCredits(3);

        anotherSubscription = new TaskSubscription()
                .setId(2L)
                .setChannelId(12)
                .setTaskType(ANOTHER_TASK_TYPE_BUFFER)
                .setLockDuration(Duration.ofMinutes(10).toMillis())
                .setCredits(2);

        mockController.initStreamProcessor(streamProcessor);
    }

    @After
    public void cleanUp()
    {
        ClockUtil.reset();
    }

    @Test
    public void shouldLockCreatedTask()
    {
        // given
        streamProcessor.addSubscription(subscription);

        // when
        mockController.processEvent(2L, event -> event
                .setEventType(TaskEventType.CREATED)
                .setType(TASK_TYPE_BUFFER, 0, TASK_TYPE_BUFFER.capacity()));

        // then
        final WrittenEvent<TaskEvent> lastWrittenEvent = mockController.getLastWrittenEvent();

        final TaskEvent taskEvent = lastWrittenEvent.getValue();
        assertThat(taskEvent.getEventType()).isEqualTo(TaskEventType.LOCK);
        assertThat(taskEvent.getLockTime()).isEqualTo(lockTimeOf(subscription));

        final BrokerEventMetadata metadata = lastWrittenEvent.getMetadata();
        assertThat(metadata.getSubscriptionId()).isEqualTo(subscription.getId());
        assertThat(metadata.getReqChannelId()).isEqualTo(subscription.getChannelId());
        assertThat(metadata.getProtocolVersion()).isEqualTo(Constants.PROTOCOL_VERSION);
        assertThat(metadata.getEventType()).isEqualTo(TASK_EVENT);
    }

    @Test
    public void shouldLockExpiredTask()
    {
        // given
        streamProcessor.addSubscription(subscription);

        // when
        mockController.processEvent(2L, event -> event
                .setEventType(TaskEventType.LOCK_EXPIRED)
                .setType(TASK_TYPE_BUFFER, 0, TASK_TYPE_BUFFER.capacity()));

        // then
        final WrittenEvent<TaskEvent> lastWrittenEvent = mockController.getLastWrittenEvent();

        final TaskEvent taskEvent = lastWrittenEvent.getValue();
        assertThat(taskEvent.getEventType()).isEqualTo(TaskEventType.LOCK);
        assertThat(taskEvent.getLockTime()).isEqualTo(lockTimeOf(subscription));

        final BrokerEventMetadata metadata = lastWrittenEvent.getMetadata();
        assertThat(metadata.getSubscriptionId()).isEqualTo(subscription.getId());
        assertThat(metadata.getReqChannelId()).isEqualTo(subscription.getChannelId());
        assertThat(metadata.getEventType()).isEqualTo(TASK_EVENT);
    }

    @Test
    public void shouldIgnoreTaskWithOtherType()
    {
        // given
        streamProcessor.addSubscription(subscription);

        // when
        mockController.processEvent(2L, event -> event
                .setEventType(TaskEventType.CREATED)
                .setType(ANOTHER_TASK_TYPE_BUFFER, 0, ANOTHER_TASK_TYPE_BUFFER.capacity()));

        // then
        assertThat(mockController.getWrittenEvents()).hasSize(0);
    }

    @Test
    public void shouldLockTasksWithMultipleSubscriptionsAndDifferentTypes()
    {
        // given
        streamProcessor.addSubscription(subscription);
        streamProcessor.addSubscription(anotherSubscription);

        // when process events with type 'x' then they should be locked by subscription 'x'
        Stream.of(1L, 2L).forEach(key ->
        {
            mockController.processEvent(key, event -> event
                    .setEventType(TaskEventType.CREATED)
                    .setType(TASK_TYPE_BUFFER, 0, TASK_TYPE_BUFFER.capacity()));

            assertThat(mockController.getLastWrittenEventValue().getEventType()).isEqualTo(TaskEventType.LOCK);
            assertThat(mockController.getLastWrittenEventMetadata().getSubscriptionId()).isEqualTo(subscription.getId());
            assertThat(mockController.getLastWrittenEventMetadata().getEventType()).isEqualTo(TASK_EVENT);
        });

        // when process events with type 'y' then they should be locked by subscription 'y'
        Stream.of(3L, 4L).forEach(key ->
        {
            mockController.processEvent(key, event -> event
                    .setEventType(TaskEventType.CREATED)
                    .setType(ANOTHER_TASK_TYPE_BUFFER, 0, ANOTHER_TASK_TYPE_BUFFER.capacity()));

            assertThat(mockController.getLastWrittenEventValue().getEventType()).isEqualTo(TaskEventType.LOCK);
            assertThat(mockController.getLastWrittenEventMetadata().getSubscriptionId()).isEqualTo(anotherSubscription.getId());
            assertThat(mockController.getLastWrittenEventMetadata().getEventType()).isEqualTo(TASK_EVENT);
        });
    }

    @Test
    public void shouldLockTasksWithMultipleSubscriptionsAndSameType()
    {
        // given
        final TaskSubscription subscriptionWithSameType = anotherSubscription.setTaskType(TASK_TYPE_BUFFER);

        streamProcessor.addSubscription(subscription);
        streamProcessor.addSubscription(subscriptionWithSameType);

        // when process the first event then it should be locked by the first subscription
        mockController.processEvent(2L, event -> event
                .setEventType(TaskEventType.CREATED)
                .setType(TASK_TYPE_BUFFER, 0, TASK_TYPE_BUFFER.capacity()));

        assertThat(mockController.getLastWrittenEventValue().getEventType()).isEqualTo(TaskEventType.LOCK);
        assertThat(mockController.getLastWrittenEventMetadata().getSubscriptionId()).isEqualTo(subscription.getId());
        assertThat(mockController.getLastWrittenEventMetadata().getEventType()).isEqualTo(TASK_EVENT);

        // when process the next event then it should be locked by the next subscription
        mockController.processEvent(3L, event -> event
                .setEventType(TaskEventType.CREATED)
                .setType(TASK_TYPE_BUFFER, 0, TASK_TYPE_BUFFER.capacity()));

        assertThat(mockController.getLastWrittenEventValue().getEventType()).isEqualTo(TaskEventType.LOCK);
        assertThat(mockController.getLastWrittenEventMetadata().getSubscriptionId()).isEqualTo(2L);
        assertThat(mockController.getLastWrittenEventMetadata().getEventType()).isEqualTo(TASK_EVENT);
    }

    @Test
    public void shouldLockTasksUnitSubscriptionHasNoMoreCredits()
    {
        // given
        streamProcessor.addSubscription(subscription);

        // when process as much events as available credits then they should be locked
        Stream.of(1, 2, 3).forEach(key ->
        {
            mockController.processEvent(key, event -> event
                    .setEventType(TaskEventType.CREATED)
                    .setType(TASK_TYPE_BUFFER, 0, TASK_TYPE_BUFFER.capacity()));

            assertThat(mockController.getLastWrittenEventValue().getEventType()).isEqualTo(TaskEventType.LOCK);
        });

        // when process one more event then it should not be locked
        mockController.processEvent(4L, event -> event
                .setEventType(TaskEventType.CREATED)
                .setType(TASK_TYPE_BUFFER, 0, TASK_TYPE_BUFFER.capacity()));

        assertThat(mockController.getWrittenEvents()).hasSize(3);
    }

    @Test
    public void shouldUpdateSubscriptionCredits()
    {
        // given
        streamProcessor.addSubscription(subscription);

        // process as much events as available credits
        Stream.of(1, 2, 3).forEach(key ->
        {
            mockController.processEvent(key, event -> event
                    .setEventType(TaskEventType.CREATED)
                    .setType(TASK_TYPE_BUFFER, 0, TASK_TYPE_BUFFER.capacity()));

            assertThat(mockController.getLastWrittenEventValue().getEventType()).isEqualTo(TaskEventType.LOCK);
        });

        // when
        streamProcessor.updateSubscriptionCredits(subscription.getId(), 1);

        mockController.processEvent(4L, event -> event
                .setEventType(TaskEventType.CREATED)
                .setType(TASK_TYPE_BUFFER, 0, TASK_TYPE_BUFFER.capacity()));

        // then
        final WrittenEvent<TaskEvent> lastWrittenEvent = mockController.getLastWrittenEvent();
        assertThat(lastWrittenEvent.getKey()).isEqualTo(4L);
        assertThat(lastWrittenEvent.getValue().getEventType()).isEqualTo(TaskEventType.LOCK);
    }

    @Test
    public void shouldRemoveSubscription()
    {
        // given
        streamProcessor.addSubscription(subscription);
        streamProcessor.addSubscription(anotherSubscription);

        // when remove a subscription of a type
        CompletableFuture<Boolean> future = streamProcessor.removeSubscription(subscription.getId());

        // then an event with this type should not be locked
        mockController.processEvent(2L, event -> event
                .setEventType(TaskEventType.CREATED)
                .setType(TASK_TYPE_BUFFER, 0, TASK_TYPE_BUFFER.capacity()));

        assertThat(mockController.getWrittenEvents()).hasSize(0);

        assertThat(future).isCompletedWithValue(true);
        assertThat(streamProcessor.isSuspended()).isFalse();

        // when remove the last subscription
        future = streamProcessor.removeSubscription(anotherSubscription.getId());

        mockController.processEvent(3L, event -> event
                .setEventType(TaskEventType.CREATED)
                .setType(ANOTHER_TASK_TYPE_BUFFER, 0, ANOTHER_TASK_TYPE_BUFFER.capacity()));

        assertThat(mockController.getWrittenEvents()).hasSize(0);

        // then the stream processor should be suspend
        assertThat(future).isCompletedWithValue(false);
        assertThat(streamProcessor.isSuspended()).isTrue();
    }

    @Test
    public void shouldSuspendIfAllSubscriptionsHasNoCredits()
    {
        // given
        streamProcessor.addSubscription(subscription);
        streamProcessor.addSubscription(anotherSubscription);

        // when process as much events as available credits
        Stream.of(1, 2, 3).forEach(key ->
        {
            mockController.processEvent(key, event -> event
                    .setEventType(TaskEventType.CREATED)
                    .setType(TASK_TYPE_BUFFER, 0, TASK_TYPE_BUFFER.capacity()));
        });

        assertThat(streamProcessor.isSuspended()).isFalse();

        Stream.of(1, 2, 3).forEach(key ->
        {
            mockController.processEvent(key, event -> event
                    .setEventType(TaskEventType.CREATED)
                    .setType(ANOTHER_TASK_TYPE_BUFFER, 0, ANOTHER_TASK_TYPE_BUFFER.capacity()));
        });

        // then the stream processor should be suspended
        assertThat(streamProcessor.isSuspended()).isTrue();
    }

    @Test
    public void shouldContinueProcessingIfUpdateSubscriptionCredits()
    {
        // given
        streamProcessor.addSubscription(subscription);

        // process as much events as available credits
        Stream.of(1, 2, 3).forEach(key ->
        {
            mockController.processEvent(key, event -> event
                    .setEventType(TaskEventType.CREATED)
                    .setType(TASK_TYPE_BUFFER, 0, TASK_TYPE_BUFFER.capacity()));
        });

        assertThat(streamProcessor.isSuspended()).isTrue();

        // when
        streamProcessor.updateSubscriptionCredits(subscription.getId(), 2);

        // then
        mockController.processEvent(4L, event -> event
                .setEventType(TaskEventType.CREATED)
                .setType(TASK_TYPE_BUFFER, 0, TASK_TYPE_BUFFER.capacity()));

        final WrittenEvent<TaskEvent> lastWrittenEvent = mockController.getLastWrittenEvent();
        assertThat(lastWrittenEvent.getKey()).isEqualTo(4L);
        assertThat(lastWrittenEvent.getValue().getEventType()).isEqualTo(TaskEventType.LOCK);

        assertThat(streamProcessor.isSuspended()).isFalse();
    }

    @Test
    public void shouldContinueProcessingIfAddSubscription()
    {
        // given
        assertThat(streamProcessor.isSuspended()).isTrue();

        // when
        streamProcessor.addSubscription(subscription);

        // then
        mockController.processEvent(2L, event -> event
                .setEventType(TaskEventType.CREATED)
                .setType(TASK_TYPE_BUFFER, 0, TASK_TYPE_BUFFER.capacity()));

        assertThat(mockController.getLastWrittenEventValue().getEventType()).isEqualTo(TaskEventType.LOCK);

        assertThat(streamProcessor.isSuspended()).isFalse();
    }

    @Test
    public void shouldFailToAddSubscriptionIfNull()
    {
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("subscription must not be null");

        streamProcessor.addSubscription(null);
    }

    @Test
    public void shouldFailToAddSubscriptionIfZeroCredits()
    {
        final TaskSubscription subscription = anotherSubscription.setCredits(0);

        thrown.expect(RuntimeException.class);
        thrown.expectMessage("subscription credits must be greater than 0");

        streamProcessor.addSubscription(subscription);
    }

    @Test
    public void shouldFailToUpdateSubscriptionCreditsIfZero()
    {
        // given
        streamProcessor.addSubscription(subscription);

        // then
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("subscription credits must be greater than 0");

        streamProcessor.updateSubscriptionCredits(subscription.getId(), 0);
    }

    @Test
    public void shouldFailToUpdateSubscriptionCreditsIfNotExist()
    {
        // when
        final CompletableFuture<Void> future = streamProcessor.updateSubscriptionCredits(123L, 5);

        mockController.processEvent(2L, event -> event
                .setEventType(TaskEventType.CREATED)
                .setType(TASK_TYPE_BUFFER, 0, TASK_TYPE_BUFFER.capacity()));

        // then
        assertThat(future).hasFailedWithThrowableThat()
            .isInstanceOf(RuntimeException.class)
            .hasMessage("Cannot update the subscription credits. Subscription with id '123' not found.");
    }

    protected long lockTimeOf(TaskSubscription subscription)
    {
        return ClockUtil.getCurrentTime().plusMillis(subscription.getLockTime()).toEpochMilli();
    }

}
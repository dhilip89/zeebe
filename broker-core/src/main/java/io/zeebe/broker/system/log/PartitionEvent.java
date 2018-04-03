/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.system.log;

import org.agrona.DirectBuffer;
import io.zeebe.broker.clustering2.base.topology.dto.BrokerDto;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.msgpack.property.EnumProperty;
import io.zeebe.msgpack.property.IntegerProperty;
import io.zeebe.msgpack.property.LongProperty;
import io.zeebe.msgpack.property.ObjectProperty;
import io.zeebe.msgpack.property.StringProperty;

public class PartitionEvent extends UnpackedObject
{
    protected final EnumProperty<PartitionState> state = new EnumProperty<>("state", PartitionState.class);
    protected final StringProperty topicName = new StringProperty("topicName");
    protected final IntegerProperty id = new IntegerProperty("id");

    // TODO: this property can be removed when we have timestamps in log entries
    protected final LongProperty creationTimeout = new LongProperty("creationTimeout", -1L);

    protected final ObjectProperty<BrokerDto> creator = new ObjectProperty<>("creator", new BrokerDto());

    public PartitionEvent()
    {
        this
            .declareProperty(state)
            .declareProperty(id)
            .declareProperty(topicName)
            .declareProperty(creationTimeout)
            .declareProperty(creator);
    }

    public void setState(PartitionState state)
    {
        this.state.setValue(state);
    }

    public PartitionState getState()
    {
        return state.getValue();
    }

    public void setTopicName(DirectBuffer buffer)
    {
        this.topicName.setValue(buffer);
    }

    public DirectBuffer getTopicName()
    {
        return topicName.getValue();
    }

    public void setId(int id)
    {
        this.id.setValue(id);
    }

    public int getId()
    {
        return id.getValue();
    }

    public void setCreationTimeout(long timeout)
    {
        creationTimeout.setValue(timeout);
    }

    public long getCreationTimeout()
    {
        return creationTimeout.getValue();
    }

    public void setCreator(DirectBuffer host, int port)
    {
        final BrokerDto address = creator.getValue();
        address.setHost(host, 0, host.capacity());
        address.setPort(port);
    }

    public BrokerDto getCreator()
    {
        return creator.getValue();
    }
}

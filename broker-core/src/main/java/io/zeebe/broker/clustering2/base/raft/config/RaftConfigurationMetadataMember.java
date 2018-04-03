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
package io.zeebe.broker.clustering2.base.raft.config;

import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.msgpack.property.IntegerProperty;
import io.zeebe.msgpack.property.StringProperty;
import org.agrona.DirectBuffer;

public class RaftConfigurationMetadataMember extends UnpackedObject
{
    protected StringProperty hostProp = new StringProperty("host");
    protected IntegerProperty portProp = new IntegerProperty("port");

    public RaftConfigurationMetadataMember()
    {
        this.declareProperty(hostProp)
            .declareProperty(portProp);
    }

    public DirectBuffer getHost()
    {
        return hostProp.getValue();
    }

    public RaftConfigurationMetadataMember setHost(final DirectBuffer host, final int offset, final int length)
    {
        this.hostProp.setValue(host, offset, length);
        return this;
    }

    public int getPort()
    {
        return portProp.getValue();
    }

    public RaftConfigurationMetadataMember setPort(final int port)
    {
        portProp.setValue(port);
        return this;
    }

}

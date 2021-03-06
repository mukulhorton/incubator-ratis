/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.ratis.examples.arithmetic.cli;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import org.apache.ratis.RaftConfigKeys;
import org.apache.ratis.conf.RaftProperties;
import org.apache.ratis.examples.arithmetic.ArithmeticStateMachine;
import org.apache.ratis.examples.filestore.FileStoreStateMachine;
import org.apache.ratis.grpc.GrpcConfigKeys;
import org.apache.ratis.protocol.RaftGroup;
import org.apache.ratis.protocol.RaftGroupId;
import org.apache.ratis.protocol.RaftPeer;
import org.apache.ratis.protocol.RaftPeerId;
import org.apache.ratis.rpc.SupportedRpcType;
import org.apache.ratis.server.RaftServer;
import org.apache.ratis.server.RaftServerConfigKeys;
import org.apache.ratis.shaded.com.google.protobuf.ByteString;
import org.apache.ratis.statemachine.StateMachine;
import org.apache.ratis.util.NetUtils;
import org.apache.ratis.util.SizeInBytes;
import org.apache.ratis.util.TimeDuration;

import java.io.File;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Class to start a ratis arithmetic example server.
 */
@Parameters(commandDescription = "Start an arithmetic server")
public class Server extends SubCommandBase {

  @Parameter(names = {"--id",
      "-i"}, description = "Raft id of this server", required = true)
  private String id;

  @Parameter(names = {"--storage",
      "-s"}, description = "Storage dir", required = true)
  private File storageDir;


  @Override
  public void run() throws Exception {
    RaftPeerId peerId = RaftPeerId.valueOf(id);
    RaftProperties properties = new RaftProperties();
    long raftSegmentPreallocatedSize = 1024 * 1024 * 1024;
    long raftSegmentMaxSize = 10 * raftSegmentPreallocatedSize;

    RaftPeer[] peers = getPeers();
    final int port = NetUtils.createSocketAddr(getPeer(peerId).getAddress()).getPort();
    GrpcConfigKeys.Server.setPort(properties, port);
    RaftConfigKeys.Rpc.setType(properties, SupportedRpcType.GRPC);

    properties.setInt(GrpcConfigKeys.OutputStream.RETRY_TIMES_KEY, Integer.MAX_VALUE);
    RaftServerConfigKeys.setStorageDir(properties, storageDir);

    RaftConfigKeys.Rpc.setType(properties, SupportedRpcType.GRPC);
    GrpcConfigKeys.setMessageSizeMax(properties,
        SizeInBytes.valueOf(raftSegmentPreallocatedSize));
    RaftServerConfigKeys.Log.Appender.setBatchEnabled(properties, true);
    RaftServerConfigKeys.Log.Appender.setBufferCapacity(properties,
        SizeInBytes.valueOf(raftSegmentPreallocatedSize));
    RaftServerConfigKeys.Log.setWriteBufferSize(properties,
        SizeInBytes.valueOf(raftSegmentPreallocatedSize));
    RaftServerConfigKeys.Log.setPreallocatedSize(properties,
        SizeInBytes.valueOf(raftSegmentPreallocatedSize));
    RaftServerConfigKeys.Log.setSegmentSizeMax(properties,
        SizeInBytes.valueOf(raftSegmentMaxSize));

    RaftServerConfigKeys.Log.setMaxCachedSegmentNum(properties, 6);

    RaftServerConfigKeys.Rpc.setRequestTimeout(properties,
        TimeDuration.valueOf(20000, TimeUnit.MILLISECONDS));
    RaftServerConfigKeys.Rpc.setTimeoutMin(properties,
        TimeDuration.valueOf(8000, TimeUnit.MILLISECONDS));
    RaftServerConfigKeys.Rpc.setTimeoutMax(properties,
        TimeDuration.valueOf(10000, TimeUnit.MILLISECONDS));


    StateMachine stateMachine = new FileStoreStateMachine(properties);

    RaftGroup raftGroup = new RaftGroup(RaftGroupId.valueOf(ByteString.copyFromUtf8(raftGroupId)), peers);
    RaftServer raftServer = RaftServer.newBuilder()
        .setServerId(RaftPeerId.valueOf(id))
        .setStateMachine(stateMachine).setProperties(properties)
        .setGroup(raftGroup)
        .build();
    raftServer.start();
  }

  /**
   * @return the peer with the given id if it is in this group; otherwise, return null.
   */
  public RaftPeer getPeer(RaftPeerId id) {
    Objects.requireNonNull(id, "id == null");
    for (RaftPeer p : getPeers()) {
      if (id.equals(p.getId())) {
        return p;
      }
    }
    throw new IllegalArgumentException("Raft peer id " + id + " is not part of the raft group definitions " + peers);
  }


}

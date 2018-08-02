package in.xnnyygn.xraft.kvstore;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import in.xnnyygn.xraft.core.node.Node;
import in.xnnyygn.xraft.core.node.NodeConfig;
import in.xnnyygn.xraft.core.node.NodeId;
import in.xnnyygn.xraft.core.noderole.RoleName;
import in.xnnyygn.xraft.core.noderole.RoleStateSnapshot;
import in.xnnyygn.xraft.core.service.AddServerCommand;
import in.xnnyygn.xraft.core.service.RemoveServerCommand;
import in.xnnyygn.xraft.core.service.StateMachine;
import in.xnnyygn.xraft.kvstore.message.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class Service implements StateMachine {

    private static final Logger logger = LoggerFactory.getLogger(Service.class);
    private final Node node;
    private final ConcurrentMap<String, CommandRequest<?>> pendingCommands = new ConcurrentHashMap<>();
    private Map<String, byte[]> map = new HashMap<>();

    public Service(Node node) {
        this.node = node;
        this.node.registerStateMachine(this);
    }

    public void addServer(CommandRequest<AddServerCommand> commandRequest) {
        Redirect redirect = checkLeadership();
        if (redirect != null) {
            commandRequest.reply(redirect);
            return;
        }

        AddServerCommand command = commandRequest.getCommand();
        this.node.addServer(command.toNodeConfig());
        commandRequest.reply(Success.INSTANCE);
    }

    public void removeServer(CommandRequest<RemoveServerCommand> commandRequest) {
        Redirect redirect = checkLeadership();
        if (redirect != null) {
            commandRequest.reply(redirect);
            return;
        }

        RemoveServerCommand command = commandRequest.getCommand();
        node.removeServer(command.getNodeId());
        commandRequest.reply(Success.INSTANCE);
    }

    public void set(CommandRequest<SetCommand> commandRequest) {
        Redirect redirect = checkLeadership();
        if (redirect != null) {
            commandRequest.reply(redirect);
            return;
        }

        SetCommand command = commandRequest.getCommand();
        logger.info("set {}", command.getKey());
        this.pendingCommands.put(command.getRequestId(), commandRequest);
        commandRequest.addCloseListener(() -> pendingCommands.remove(command.getRequestId()));
        this.node.appendLog(command.toBytes());
    }

    public void get(CommandRequest<GetCommand> commandRequest) {
        String key = commandRequest.getCommand().getKey();
        logger.info("get {}", key);
        byte[] value = this.map.get(key);
        // TODO view from node state machine
        commandRequest.reply(new GetCommandResponse(value));
    }

    @Override
    public void applyCommand(byte[] commandBytes) {
        SetCommand command = SetCommand.fromBytes(commandBytes);
        this.map.put(command.getKey(), command.getValue());

        CommandRequest<?> commandRequest = this.pendingCommands.remove(command.getRequestId());
        if (commandRequest != null) {
            commandRequest.reply(Success.INSTANCE);
        }
    }

    @Override
    public byte[] generateSnapshot() {
        return toSnapshot(this.map);
    }

    @Override
    public void applySnapshot(byte[] snapshot) {
        logger.info("apply snapshot");
        this.map = fromSnapshot(snapshot);
    }

    private Redirect checkLeadership() {
        RoleStateSnapshot state = this.node.getRoleState();
        if (state.getRole() == RoleName.FOLLOWER) {
            NodeId leaderId = state.getLeaderId();
            return new Redirect(leaderId != null ? leaderId.getValue() : null);
        }

        if (state.getRole() == RoleName.CANDIDATE) {
            return new Redirect(null);
        }
        return null;
    }

    static byte[] toSnapshot(Map<String, byte[]> map) {
        Protos.EntryList.Builder entryList = Protos.EntryList.newBuilder();
        for (Map.Entry<String, byte[]> entry : map.entrySet()) {
            entryList.addEntries(
                    Protos.EntryList.Entry.newBuilder()
                            .setKey(entry.getKey())
                            .setValue(ByteString.copyFrom(entry.getValue())).build()
            );
        }
        return entryList.build().toByteArray();
    }

    static Map<String, byte[]> fromSnapshot(byte[] snapshot) {
        Map<String, byte[]> map = new HashMap<>();
        try {
            Protos.EntryList entryList = Protos.EntryList.parseFrom(snapshot);
            for (Protos.EntryList.Entry entry : entryList.getEntriesList()) {
                map.put(entry.getKey(), entry.getValue().toByteArray());
            }
            return map;
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalStateException("failed to load map from snapshot", e);
        }
    }

}

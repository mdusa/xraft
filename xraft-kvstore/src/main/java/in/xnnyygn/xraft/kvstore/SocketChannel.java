package in.xnnyygn.xraft.kvstore;

import in.xnnyygn.xraft.core.node.NodeId;
import in.xnnyygn.xraft.core.service.Channel;
import in.xnnyygn.xraft.core.service.ChannelException;
import in.xnnyygn.xraft.core.service.RedirectException;
import in.xnnyygn.xraft.kvstore.command.GetCommand;
import in.xnnyygn.xraft.kvstore.command.SetCommand;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;

public class SocketChannel implements Channel {

    private final String host;
    private final int port;

    public SocketChannel(String host, int port) {
        this.host = host;
        this.port = port;
    }

    @Override
    public Object send(Object payload) {
        TTransport transport = new TSocket(this.host, this.port);
        TProtocol protocol = new TBinaryProtocol(transport);
        KVStore.Iface client = new KVStore.Client(protocol);
        try {
            transport.open();
            return dispatch(payload, client);
        } catch (Redirect redirect) {
            if (redirect.getLeaderId() != null) {
                throw new RedirectException(new NodeId(redirect.getLeaderId()));
            }
            throw new ChannelException("redirect", redirect);
        } catch (TException e) {
            throw new ChannelException("failed to send", e);
        } finally {
            transport.close();
        }
    }

    private Object dispatch(Object payload, KVStore.Iface client) throws TException {
        if (payload instanceof SetCommand) {
            SetCommand command = (SetCommand) payload;
            client.Set(command.getKey(), command.getValue());
            return null;
        } else if (payload instanceof GetCommand) {
            GetResult result = client.Get(((GetCommand) payload).getKey());
            return result.isFound() ? result.getValue() : null;
        }
        throw new ChannelException("unexpected payload type " + payload.getClass());
    }

}

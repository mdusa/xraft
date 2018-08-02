package in.xnnyygn.xraft.core.log.entry;

import java.util.List;

public interface Entry {

    int KIND_NO_OP = 0;
    int KIND_GENERAL = 1;
    @Deprecated
    int KIND_GROUP_CONFIG = 2;
    int KIND_ADD_NODE = 3;
    int KIND_REMOVE_NODE = 4;

    int getKind();

    int getIndex();

    int getTerm();

    byte[] getCommandBytes();

    List<EntryListener> getListeners();

    void removeAllListeners();

}

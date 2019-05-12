package actors.master;

import java.io.Serializable;

public interface MasterProtocol {
    class WorkerReceptionCheck implements MasterProtocol, Serializable {
    }
}

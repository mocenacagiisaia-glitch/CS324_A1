package edu.usp.cs324.client;

import edu.usp.cs324.api.*;
import java.rmi.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClusterClientTest {
    @Test void onlyPreAdmissionEnvelopesAreRetryable() {
        RetryException retry = new RetryException("stale term");
        assertTrue(ClusterClient.preAdmission(retry));
        assertTrue(ClusterClient.preAdmission(new ServerException("RMI", new ServerException("RMI", retry))));
        assertFalse(ClusterClient.preAdmission(new ServerException("RMI", new JobFailedException("accepted", retry))));
        assertFalse(ClusterClient.preAdmission(new RemoteException("completion unknown", retry)));
        assertFalse(ClusterClient.preAdmission(new java.rmi.ConnectException("offline")));
    }
}

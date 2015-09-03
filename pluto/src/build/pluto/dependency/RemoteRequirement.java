package build.pluto.dependency;

import java.io.File;
import java.io.IOException;

import org.sugarj.common.FileCommands;
import org.sugarj.common.Log;

import build.pluto.builder.BuildManager;

public abstract class RemoteRequirement implements Requirement {

    private static final long serialVersionUID = -637138545509445926L;

    private long consistencyCheckInterval;

    private File persistentPath;

    public RemoteRequirement(File persistentPath, long consistencyCheckInterval) {
        this.persistentPath = persistentPath;
        this.consistencyCheckInterval = consistencyCheckInterval;
        long timestamp = getStartingTimestamp();
        this.writePersistentPath(timestamp);
    }

    /**
     * This implementation calls needsConsistencyCheck and isConsistentWithRemote
     */
    public boolean isConsistent() {
        long timestamp = getStartingTimestamp();
        if(needsConsistencyCheck(timestamp)) {
            Log.log.log("Check if the remote resource is consistent with the local resource", Log.CORE);
            if(isConsistentWithRemote()){
                writePersistentPath(timestamp);
                return true;
            } else {
                return false;
            }
        }
        return true;
    }

    protected long getStartingTimestamp() {
        Thread currentThread = Thread.currentThread();
        return BuildManager.getStartingTimeOfBuildManager(currentThread);
    }

    /**
     * Checks if the local state is consistent with the remote state.
     * @return true if local state is consistent with remote state.
     */
    protected abstract boolean isConsistentWithRemote();

    /**
     * Checks if a consistencycheck needs to be made.
     * @param currentTime the time to check if the consistency needs to be checked.
     * @return true if a consistencycheck needs to be made.
     */
    private boolean needsConsistencyCheck(long currentTime) {
        if (!FileCommands.exists(persistentPath)) {
            writePersistentPath(currentTime);
            return true;
        }
        long lastConsistencyCheck = readPersistentPath();
        if (lastConsistencyCheck + consistencyCheckInterval < currentTime) {
            return true;
        }
        return false;
    }

    private long readPersistentPath() {
        try {
            String persistentPathContent =
                FileCommands.readFileAsString(persistentPath);
            return Long.parseLong(persistentPathContent.replace("\n", ""));
        } catch(IOException e) {
            Log.log.logErr("There occured an error reading the persistentPath "
                    + "of a RemoteRequirement", Log.CORE);
        } catch(NumberFormatException e) {
            Log.log.logErr("The content of the persistentPath of a "
                    + "RemoteRequirement was not correctly written previously",
                    Log.CORE);
        }
        //timestamp file was not found or was not correctly written.
        //Therefore we need to force a consistencycheck.
        return 0L;
    }

    private void writePersistentPath(long timeStamp) {
        try {
            if(!persistentPath.exists()) {
                FileCommands.createFile(persistentPath);
            }
            FileCommands.writeToFile(persistentPath, String.valueOf(timeStamp));
        } catch(IOException e) {
            Log.log.logErr("There occured an error when creating or writing"
                    + " the persistentPath of a RemoteRequirement", Log.CORE);
        }
    }
}
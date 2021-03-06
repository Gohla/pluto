package build.pluto.dependency;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Externalizable;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.util.Objects;

import build.pluto.BuildUnit;
import build.pluto.builder.BuildManager;
import build.pluto.builder.BuildRequest;
import build.pluto.builder.BuildUnitProvider;
import build.pluto.output.Output;
import build.pluto.output.OutputPersisted;
import build.pluto.output.OutputStamp;

public class BuildRequirement<Out extends Output> implements Requirement, Externalizable {

  private static final long serialVersionUID = -5059819155907677962L;
  
  private BuildUnit<Out> unit;
  private boolean hasFailed;
  private BuildRequest<?, Out, ?, ?> req;
  private OutputStamp stamp;

  public BuildRequirement() { }

  public BuildRequirement(BuildUnit<Out> unit, BuildRequest<?, Out, ?, ?> req) {
    this(
        Objects.requireNonNull(unit, "unit"), 
        Objects.requireNonNull(req, "request"), 
        req.stamper.stampOf(unit.getBuildResult()));
    if (BuildManager.ASSERT_SERIALIZABLE && !assertStampSerializable()) {
      throw new AssertionError("Stamp of deserialized result is not equal to stamp of result of " + unit.getGeneratedBy().createBuilder().description() + "  " + ((build.pluto.output.Out<?>) unit.getBuildResult()).val().getClass());
    }
  }
  
  public BuildRequirement(BuildUnit<Out> unit, BuildRequest<?, Out, ?, ?> req, OutputStamp stamp) {
    this.unit = Objects.requireNonNull(unit, "unit");
    this.req = Objects.requireNonNull(req, "request");
    this.stamp = stamp;
  }

  private boolean assertStampSerializable() {
    if (!(unit.getBuildResult() instanceof OutputPersisted<?>))
      return true;
    try {
      ByteArrayOutputStream memBufferOutput = new ByteArrayOutputStream();
      ObjectOutputStream oStream = new ObjectOutputStream(memBufferOutput);
      oStream.writeObject(unit.getBuildResult());
      ByteArrayInputStream memBufferInput = new ByteArrayInputStream(memBufferOutput.toByteArray());
      ObjectInputStream iStream = new ObjectInputStream(memBufferInput);
      @SuppressWarnings("unchecked")
      Out deserializedOutput = (Out) iStream.readObject();
      return req.stamper.stampOf(deserializedOutput).equals(stamp);
    } catch (Exception e) {
      e.printStackTrace();
      return false;
    }
  }

  protected boolean isHasFailed() {
    return hasFailed;
  }

  protected OutputStamp getStamp() {
    return stamp;
  }

  @Override
  public boolean isConsistent() {
    boolean reqsEqual = unit.getGeneratedBy().deepEquals(req);
    if (!reqsEqual)
      return false;
    
    boolean stampOK = stamp == null || stamp.equals(stamp.getStamper().stampOf(this.unit.getBuildResult()));
    if (!stampOK)
      return false;
    
    return true;
  }
  
  @Override
  public boolean tryMakeConsistent(BuildUnitProvider manager) throws IOException {
    boolean wasFailed = hasFailed || unit != null && unit.hasFailed();
    BuildUnit<Out> newUnit = manager.require(this.req, false).getUnit();
    hasFailed = newUnit.hasFailed();

    if (wasFailed && !hasFailed)
      return false;
    
    boolean stampOK = stamp == null || stamp.equals(stamp.getStamper().stampOf(newUnit.getBuildResult()));
    if (!stampOK)
      return false;
    
    return true;
   
  }

  @Override
  public String toString() {
    return req.toString();
  }

  @Override
  public void writeExternal(ObjectOutput out) throws IOException {
    out.writeObject(unit.getPersistentPath());
    out.writeBoolean(hasFailed);
    out.writeObject(req);
    out.writeObject(stamp);
  }

  @Override
  @SuppressWarnings("unchecked")
  public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
    File unitPath = (File) in.readObject();
    unit = BuildUnit.read(unitPath);
    if (unit == null)
      throw new IOException("Could not read build unit " + unitPath);
    hasFailed = in.readBoolean();
    req = (BuildRequest<?, Out, ?, ?>) in.readObject();
    stamp = (OutputStamp) in.readObject();
  }

  public BuildUnit<Out> getUnit() {
    return unit;
  }

  public BuildRequest<?, Out, ?, ?> getRequest() {
    return req;
  }
}

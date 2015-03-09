package org.sugarj.cleardep.build;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.sugarj.cleardep.BuildUnit;
import org.sugarj.cleardep.BuildUnit.State;
import org.sugarj.cleardep.build.BuildCycle.Result;
import org.sugarj.cleardep.dependency.BuildRequirement;
import org.sugarj.cleardep.output.BuildOutput;
import org.sugarj.common.Log;

public abstract class FixpointCycleSupport implements CycleSupport {

  public static class FixpointEntry
    < In  extends Serializable, 
      Out extends BuildOutput,
      B extends Builder<In, Out>,
      F extends BuilderFactory<In, Out, B>> {
    
    private F builderFactory;
    private Class<In> inputClass;
    
    public FixpointEntry(F factory, Class<In> inputClass) {
      super();
      this.builderFactory = factory;
      this.inputClass = inputClass;
    }
    
    private B makeBuilder(BuildRequirement<Out> req) {
      return this.builderFactory.makeBuilder((In)req.req.input);
    }
    
    private Out compile(FixpointCycleBuildResultProvider cycleManager, BuildRequirement<Out> req) throws Throwable{
      Builder<In, Out> builder = makeBuilder(req);
      Log.log.beginTask(builder.taskDescription(), Log.CORE);
      Out result = builder.triggerBuild(req.unit, cycleManager);
      req.unit.setBuildResult(result);
      req.unit.setState(State.finished(true));
      Log.log.endTask();
      return result;
    }
    
  }
  
  private List<FixpointEntry<?,?,?,?>> supportedBuilders;
  
  public FixpointCycleSupport(FixpointEntry<?,?,?,?> ... supportedBuilders) {
    this.supportedBuilders = Arrays.asList(supportedBuilders);
  }
  
  protected static
  < In  extends Serializable, 
  Out extends BuildOutput,
  B extends Builder<In, Out>,
  F extends BuilderFactory<In, Out, B>>
   FixpointEntry<In, Out, B, F> entry(F factory, Class<In> inputClass) {
    return new FixpointEntry<In, Out, B, F>(factory, inputClass);
  }
  
  @Override
  public String getCycleDescription(BuildCycle cycle) {
    String cycleName = "Cycle ";
    for (BuildRequirement<?> req : cycle.getCycleComponents()) {
      cycleName += getBuilderForInput(req.req.input).makeBuilder((BuildRequirement) req).taskDescription() + ", ";
    }
    return cycleName;
  }
  
  private FixpointEntry<?, ?,?, ?> getBuilderForInput(Serializable input) {
    for (FixpointEntry<?, ?, ?,?> supportedBuilder : supportedBuilders) {
      if (supportedBuilder.inputClass.isAssignableFrom(input.getClass())) {
        return supportedBuilder;
      }
    }
    return null;
  }
  
  private boolean canCompileInput(Serializable input) {
    return this.getBuilderForInput(input) != null;
  }

  @Override
  public boolean canCompileCycle(BuildCycle cycle) {
    for (BuildRequirement<?> req : cycle.getCycleComponents()) {
      if (!canCompileInput(req.req.input)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public Result compileCycle(BuildUnitProvider manager, BuildCycle cycle) throws Throwable {
    List<FixpointEntry<?, ?, ?,?>> cycleBuilders = new ArrayList<>(cycle.getCycleComponents().size());
    for (BuildRequirement<?> req : cycle.getCycleComponents()) {
      cycleBuilders.add(getBuilderForInput(req.req.input));
    }
    
    FixpointCycleBuildResultProvider cycleManager = new FixpointCycleBuildResultProvider(manager, cycle);
    BuildCycle.Result result = new Result();
    
    int numInterations = 1;
    while (!cycle.isConsistent()) {
      Log.log.beginTask("Compile cycle iteration " + numInterations, Log.CORE);
      for (int i = cycleBuilders.size()-1; i>= 0; i--) {
        BuildRequirement req = cycle.getCycleComponents().get(i);
        FixpointEntry entry = cycleBuilders.get(i);

        req.unit = BuildUnit.create(req.unit.getPersistentPath(), req.unit.getGeneratedBy());
        result.setBuildResult(req.unit, entry.compile(cycleManager, req));
       
      }
      Log.log.endTask();
      numInterations ++;
      
    }
    Log.log.log("Fixpoint detected.", Log.CORE);
    return result;
  }

}
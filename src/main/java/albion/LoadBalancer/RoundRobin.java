package albion.LoadBalancer;

import org.cloudbus.cloudsim.allocationpolicies.VmAllocationPolicy;
import org.cloudbus.cloudsim.allocationpolicies.VmAllocationPolicySimple;
import org.cloudbus.cloudsim.brokers.DatacenterBroker;
import org.cloudbus.cloudsim.brokers.DatacenterBrokerSimple;
import org.cloudbus.cloudsim.cloudlets.Cloudlet;
import org.cloudbus.cloudsim.cloudlets.CloudletSimple;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.datacenters.Datacenter;
import org.cloudbus.cloudsim.datacenters.DatacenterSimple;
import org.cloudbus.cloudsim.distributions.ContinuousDistribution;
import org.cloudbus.cloudsim.distributions.UniformDistr;
import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.hosts.HostSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.ResourceProvisionerSimple;
import org.cloudbus.cloudsim.resources.Pe;
import org.cloudbus.cloudsim.resources.PeSimple;
import org.cloudbus.cloudsim.schedulers.cloudlet.CloudletSchedulerTimeShared;
import org.cloudbus.cloudsim.schedulers.vm.VmSchedulerTimeShared;
import org.cloudbus.cloudsim.util.TimeUtil;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModel;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModelDynamic;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModelFull;
import org.cloudbus.cloudsim.vms.Vm;
import org.cloudbus.cloudsim.vms.VmSimple;
import org.cloudsimplus.builders.tables.CloudletsTableBuilder;
import org.cloudsimplus.util.Log;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * An example showing how to implement a custom
 * {@link VmAllocationPolicy} that randomly
 * selects suitable Hosts to place submitted VMs.
 * Check the {@link #createDatacenter()} method for details.
 *
 * <p>Since CloudSim Plus provides functional
 * VmAllocationPolicy implementations, you don't
 * even need to create a new class to implement your own policy,
 * unless you really want to (to make reuse easy).</p>
 *
 * @author Manoel Campos da Silva Filho
 * @since CloudSim Plus 4.0.5
 */
public class RoundRobin {
    private static final int HOSTS = 10;
    private static final int HOST_PES = 8;

    private static final int VMS = 4;
    private static final int VM_PES = 4;

    private static final int CLOUDLETS = 4;
    private static final int CLOUDLET_PES = 2;
    private static final int CLOUDLET_LENGTH = 10000;

    private final CloudSim simulation;
    private DatacenterBroker broker0;
    private List<Vm> vmList;
    private List<Cloudlet> cloudletList;
    private Datacenter datacenter0;

    private final ContinuousDistribution random;

    public static void main(String[] args) {
        new RoundRobin();
    }

    private RoundRobin() {
        final double startSecs = TimeUtil.currentTimeSecs();
        //Enables just some level of log messages.
        Log.setLevel(ch.qos.logback.classic.Level.WARN);

        simulation = new CloudSim();

        random = new UniformDistr();
        datacenter0 = createDatacenter();

        //Creates a broker that is a software acting on behalf a cloud customer to manage his/her VMs and Cloudlets
        broker0 = new DatacenterBrokerSimple(simulation);

        vmList = createVms();
        cloudletList = createCloudlets();
        broker0.submitVmList(vmList);
        broker0.submitCloudletList(cloudletList);

        simulation.start();

        final List<Cloudlet> finishedCloudlets = broker0.getCloudletFinishedList();
        finishedCloudlets.sort(Comparator.comparingLong(cloudlet -> cloudlet.getVm().getId()));
        new CloudletsTableBuilder(finishedCloudlets).build();
        System.out.println("Execution time: " + TimeUtil.secondsToStr(TimeUtil.elapsedSeconds(startSecs)));
    }

    private Datacenter createDatacenter() {
        final List<Host> hostList = new ArrayList<>(HOSTS);
        for(int i = 0; i < HOSTS; i++) {
            Host host = createHost();
            hostList.add(host);
        }

        VmAllocationPolicySimple vmAllocationPolicy = new VmAllocationPolicySimple();

        //Replaces the default method that allocates Hosts to VMs by our own implementation
        vmAllocationPolicy.setFindHostForVmFunction(this::findRandomSuitableHostForVm);

        return new DatacenterSimple(simulation, hostList, vmAllocationPolicy);
    }

    private Optional<Host> findRandomSuitableHostForVm(final VmAllocationPolicy vmAllocationPolicy, final Vm vm) {
        final List<Host> hostList = vmAllocationPolicy.getHostList();
		int k = 0;
        for (int i = 0; i < hostList.size(); i++){
            if(k >= hostList.size()) {
				k = 0;
			}
            final Host host = hostList.get(k);
            if(host.isSuitableForVm(vm)){
                return Optional.of(host);
            }
			k++;
        }

        return Optional.empty();
    }

    private Host createHost() {
        List<Pe> peList = new ArrayList<>(HOST_PES);
        //List of Host's CPUs (Processing Elements, PEs)
        for (int i = 0; i < HOST_PES; i++) {
            peList.add(new PeSimple(1000, new PeProvisionerSimple()));
        }

        final long ram = 2048; //in Megabytes
        final long bw = 10000; //in Megabits/s
        final long storage = 1000000; //in Megabytes
        Host host = new HostSimple(ram, bw, storage, peList);
        host
            .setRamProvisioner(new ResourceProvisionerSimple())
            .setBwProvisioner(new ResourceProvisionerSimple())
            .setVmScheduler(new VmSchedulerTimeShared());
        return host;
    }

    /**
     * Creates a list of VMs.
     */
    private List<Vm> createVms() {
        final List<Vm> list = new ArrayList<>(VMS);
        for (int i = 0; i < VMS; i++) {
            Vm vm =
                new VmSimple(i, 1000, VM_PES)
                    .setRam(512).setBw(1000).setSize(10000)
                    .setCloudletScheduler(new CloudletSchedulerTimeShared());

            list.add(vm);
        }

        return list;
    }

    /**
     * Creates a list of Cloudlets.
     */
    private List<Cloudlet> createCloudlets() {
        final List<Cloudlet> list = new ArrayList<>(CLOUDLETS);
        UtilizationModel utilization = new UtilizationModelDynamic(0.2);
        for (int i = 0; i < CLOUDLETS; i++) {
            Cloudlet cloudlet =
                new CloudletSimple(i, CLOUDLET_LENGTH, CLOUDLET_PES)
                    .setFileSize(1024)
                    .setOutputSize(1024)
                    .setUtilizationModelBw(utilization)
                    .setUtilizationModelRam(utilization)
                    .setUtilizationModelCpu(new UtilizationModelFull());
            list.add(cloudlet);
        }

        return list;
    }
}

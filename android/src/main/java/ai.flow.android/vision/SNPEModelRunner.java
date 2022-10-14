package ai.flow.android.vision;

import ai.flow.vision.ModelRunner;
import android.app.Application;
import com.qualcomm.qti.snpe.FloatTensor;
import com.qualcomm.qti.snpe.NeuralNetwork;
import com.qualcomm.qti.snpe.SNPE;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import static com.qualcomm.qti.snpe.NeuralNetwork.Runtime;

public class SNPEModelRunner extends ModelRunner {
    private Application context;
    NeuralNetwork network;
    String modelPath;
    public Map<String, FloatTensor> container = new HashMap<>();
    public Map<String, int[]> shapes;
    public int warmupIters = 50;
    boolean useGPU;

    public SNPEModelRunner(Application context, String modelPath, boolean useGPU){
        this.context = context;
        this.modelPath = modelPath;
        this.useGPU = useGPU;
    }

    @Override
    public void init(Map<String, int[]> shapes) {
        this.shapes = shapes;

        SNPE.NeuralNetworkBuilder builder = null;
        File modelStream = new File(modelPath);
        try {
            builder = new SNPE.NeuralNetworkBuilder(context)
                    .setDebugEnabled(false)
                    .setPerformanceProfile(NeuralNetwork.PerformanceProfile.SUSTAINED_HIGH_PERFORMANCE)
                    .setExecutionPriorityHint(NeuralNetwork.ExecutionPriorityHint.HIGH)
                    .setModel(modelStream);

            if (useGPU)
                builder.setRuntimeOrder(Runtime.GPU_FLOAT16);
        } catch (IOException e) {
            e.printStackTrace();
        }

        assert builder != null;
        network = builder.build();

        container.put("input_imgs", network.createFloatTensor(shapes.get("input_imgs")));
        container.put("desire", network.createFloatTensor(shapes.get("desire")));
        container.put("traffic_convention", network.createFloatTensor(shapes.get("traffic_convention")));
        container.put("initial_state", network.createFloatTensor(shapes.get("initial_state")));
    }

    public void writeTensor(FloatTensor tensor, ByteBuffer buffer){
        for (int i=0; i<tensor.getSize(); i++)
            tensor.write(buffer.getFloat(i*4), i);
    }

    @Override
    public void warmup(){
        for (int i=0; i<warmupIters; i++)
            network.execute(container);
    }

    @Override
    public void run(ByteBuffer inputImgs, ByteBuffer desire, ByteBuffer trafficConvention, ByteBuffer state, float[] netOutputs){
        writeTensor(container.get("input_imgs"), inputImgs);
        writeTensor(container.get("desire"), desire);
        writeTensor(container.get("traffic_convention"), trafficConvention);
        writeTensor(container.get("initial_state"), state);

        network.execute(container).get("outputs").read(netOutputs, 0, netOutputs.length);
    }

    @Override
    public void dispose(){
        network.release();
        for (FloatTensor tensor : container.values()) {
            tensor.release();
        }
    }
}

package com.example.a40136.arcore_measure;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.Image;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.ar.core.Anchor;
import com.google.ar.core.Frame;
import com.google.ar.core.exceptions.NotYetAvailableException;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.Color;
import com.google.ar.sceneform.rendering.MaterialFactory;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.ShapeFactory;
import com.google.ar.sceneform.rendering.ViewRenderable;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.TransformableNode;

import org.opencv.android.OpenCVLoader;
import org.opencv.core.Point;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final Double MIN_OPENGL_VERSION = 3.0;


    private ArFragment arFragment;


    private Button btn_trans;
    private TextView tv_state;

    private ModelRenderable coordRenderable;

    private Frame frame;

    // 手机长宽
    private int phone_width;
    private int phone_height;

    // 图像长宽
    private int pic_width = 480;
    private int pic_height = 640;

    private ArrayList<AnchorNode> anchorNodes = new ArrayList<>();
    private ArrayList<Node> nodeForLines = new ArrayList<>();
    private ArrayList<Float> distances = new ArrayList<>();
    private ArrayList<Node> infos = new ArrayList<>();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (!checkIsSupportedDeviceOrFinish(this))return;
        
        initLoadOpenCV();
        initModel();
        initFragment();
        init();

        getWidthAndHeight();
    }

    /**
     * 加载OpenCV
     */
    private void initLoadOpenCV() {
        boolean success = OpenCVLoader.initDebug();
        if (success) {
            Log.i(TAG, "OpenCV Libraries loaded...");
            Toast.makeText(this, "OpenCV Libraries loaded...", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this.getApplicationContext(), "WARNING: Could not load OpenCV Libraries", Toast.LENGTH_SHORT).show();
        }
    }


    /**
     * 初始化控件
     */
    private void init(){
        btn_trans = findViewById(R.id.btn_trans);
        btn_trans.setOnClickListener(view ->{
            Toast.makeText(this, "转换！", Toast.LENGTH_SHORT).show();
            // 获取Bitmap用于角点检测
            Bitmap bitmap = getBitmapFromView();
            // 保存Bitmap到相册
            saveBmp2Gallery(bitmap,"aaaa");

            // 获取角点
            ArrayList<Point> points = CornorDetect.getCorner(bitmap);
            if (points == null){
                Toast.makeText(this, "检测失败", Toast.LENGTH_SHORT).show();
            }else{
                Log.d(TAG, "cornors: "+points.size());
                Log.d(TAG, "p1"+points.get(0).x+", "+points.get(0).y);
                Log.d(TAG, "p2"+points.get(1).x+", "+points.get(1).y);
                Log.d(TAG, "p3"+points.get(2).x+", "+points.get(2).y);
                Log.d(TAG, "p4"+points.get(3).x+", "+points.get(3).y);
                Toast.makeText(this, "检测成功", Toast.LENGTH_SHORT).show();

                addCornerAnchor(points);
            }
        });
        tv_state = findViewById(R.id.tv_state);
    }

    /**
     * 初始化ArFragment，以及点击创建Model事件
     */
    private void initFragment() {

        arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.ux_fragment);
        arFragment.setOnTapArPlaneListener((hitResult, plane, motionEvent) -> {
            if (coordRenderable == null){
                return;
            }

            // 创建Anchor
            Anchor anchor = hitResult.createAnchor();
            AnchorNode anchorNode = new AnchorNode(anchor);
            anchorNode.setParent(arFragment.getArSceneView().getScene());

            // 添加进链表
            anchorNodes.add(anchorNode);

            // 渲染各种Model
            drawModel();

            // 获取信息
            tv_state.setText(getString(distances));

            // 将Model添加到Anchor
            TransformableNode coord = new TransformableNode(arFragment.getTransformationSystem());
            coord.setParent(anchorNode);
            coord.setRenderable(coordRenderable);
            coord.select();
            coord.setOnTouchListener((hitTestResult, motionEvent1) -> {
                if (motionEvent1.getAction() == MotionEvent.ACTION_UP){
                    Toast.makeText(MainActivity.this, "点中了", Toast.LENGTH_SHORT).show();
                    // 渲染Model
                    drawModel();
                    // 获取信息
                    tv_state.setText(getString(distances));
                }
                return false;
            });
        });
    }

    /**
     *  画出标点之间的连线并显示其长度
     */
    private void drawModel(){
        if (anchorNodes.size()>=2){
            if (nodeForLines.size()!=0){
                for (Node node : nodeForLines) {
                    node.setRenderable(null);
                }
                nodeForLines.clear();
            }
            if(infos.size()!=0){
                for (Node info: infos){
                    info.setRenderable(null);
                }
                infos.clear();
            }

            // nodeForLines = new ArrayList<>();

            distances = getAllDistance(anchorNodes);

            // nodeForLines.clear();

            drawAllLines(anchorNodes);
            drawInfo(nodeForLines, distances);
        }
    }

    /**
     *  初始化Model资源
     */
    private void initModel() {
        ModelRenderable.builder()
                .setSource(this, Uri.parse("model.sfb"))
                .build()
                .thenAccept(renderable -> coordRenderable = renderable)
                .exceptionally(throwable -> {
                    Log.e(TAG, "Unable to load Renderable.", throwable );
                    return null;
                });
    }

    /**
     * 获取当前View中的图像，用于角点检测
     * @return Bitmap图像
     */
    private Bitmap getBitmapFromView(){
        Bitmap bitmap = null;
        try {
            Image image = arFragment.getArSceneView().getArFrame().acquireCameraImage();
            byte[] bytes = UtilsBitmap.imageToByte(image);
            bitmap = BitmapFactory.decodeByteArray(bytes,0,bytes.length,null);
            bitmap = UtilsBitmap.rotateBitmap(bitmap, 90);
        } catch (NotYetAvailableException e) {
            e.printStackTrace();
        }
        return bitmap;
    }

    /**
     *  将检测到的角点作HitTest，再将Model放置在其结果上
     * @param points 检测到的角点坐标
     */
    private void addCornerAnchor(ArrayList<Point> points){

        frame = arFragment.getArSceneView().getArFrame();
            for (Point point : points) {
                Anchor anchor = frame.hitTest(pic2screen(point)[0],pic2screen(point)[1]).get(0).createAnchor();
                AnchorNode anchorNode = new AnchorNode(anchor);
                anchorNode.setParent(arFragment.getArSceneView().getScene());

                // 将Model添加到Anchor
                TransformableNode coord = new TransformableNode(arFragment.getTransformationSystem());
                coord.setParent(anchorNode);
                coord.setRenderable(coordRenderable);
                coord.select();
                coord.setOnTouchListener((hitTestResult, motionEvent1) -> {
                    if (motionEvent1.getAction() == MotionEvent.ACTION_DOWN){
                        Toast.makeText(MainActivity.this, "点中了", Toast.LENGTH_SHORT).show();
                    }
                    return false;
                });
            }
    }

    /**
     *  获取当前手机屏幕的宽高（单位：像素）
     */
    private void getWidthAndHeight(){
        Display display = getWindowManager().getDefaultDisplay();
        android.graphics.Point outSize = new android.graphics.Point();
        display.getSize(outSize);
        phone_width = outSize.x;
        phone_height = outSize.y;
    }

    private float[] pic2screen(Point point){
        double x = point.x * phone_width / pic_width;
        double y = point.y * phone_height / pic_height;
        return new float[]{(float) x, (float) y};
    }

    /**
     * 获取链表中所有点的距离
     *
     * @param anchorNodes 锚点
     * @return
     */
    private ArrayList<Float> getAllDistance(ArrayList<AnchorNode> anchorNodes) {
        ArrayList<Float> distances = new ArrayList<>();
        for (int i = 0; i < anchorNodes.size() - 1; i++) {
            float dis = distance(anchorNodes.get(i), anchorNodes.get(i + 1));
            distances.add(dis);
        }
        return distances;
    }

    /**
     * 计算两个点之间的距离
     *
     * @param node1
     * @param node2
     * @return
     */
    private float distance(AnchorNode node1, AnchorNode node2) {
        float dx = node1.getWorldPosition().x - node2.getWorldPosition().x;
        float dy = node1.getWorldPosition().y - node2.getWorldPosition().y;
        float dz = node1.getWorldPosition().z - node2.getWorldPosition().z;
        float result = Float.valueOf(String.format("%.3f", Math.sqrt(dx * dx + dy * dy + dz * dz)));
        return result;
    }


    /**
     * 将所有计算结果表述出来
     *
     * @param allDistance
     * @return
     */
    private String getString(ArrayList<Float> allDistance) {
        String txt = "\n";
        for (int i = 0; i < allDistance.size(); i++) {
            txt += "第" + i + "段距离为：" + allDistance.get(i) +"m"+ "\n";
        }
        txt += "线数量" + nodeForLines.size() + "\n";
        txt += "Info数量" + infos.size() + "\n";
        txt += "标点数量" + anchorNodes.size();
        return txt;
    }

    /**
     * 画出顺序点之间的连线
     */
    private void drawLine(AnchorNode node1, AnchorNode node2) {
        //Draw a line between two AnchorNodes
        Log.d(TAG, "drawLine");
        Vector3 point1, point2;
        point1 = node1.getWorldPosition();
        point2 = node2.getWorldPosition();
        point1.x = Float.valueOf(String.format("%.3f", point1.x));
        point1.y = Float.valueOf(String.format("%.3f", point1.y));
        point1.z = Float.valueOf(String.format("%.3f", point1.z));
        point2.x = Float.valueOf(String.format("%.3f", point2.x));
        point2.y = Float.valueOf(String.format("%.3f", point2.y));
        point2.z = Float.valueOf(String.format("%.3f", point2.z));


        //First, find the vector extending between the two points and define a look rotation
        //in terms of this Vector.
        Node nodeForLine = new Node();
        final Vector3 difference = Vector3.subtract(point1, point2);
        final Vector3 directionFromTopToBottom = difference.normalized();
        final Quaternion rotationFromAToB =
                Quaternion.lookRotation(directionFromTopToBottom, Vector3.up());
        MaterialFactory.makeOpaqueWithColor(getApplicationContext(), new Color(238, 238, 0))
                .thenAccept(
                        material -> {
                            /* Then, create a rectangular prism, using ShapeFactory.makeCube() and use the difference vector
                                   to extend to the necessary length.  */
                            Log.d(TAG, "drawLine insie .thenAccept");
                            ModelRenderable model = ShapeFactory.makeCube(
                                    new Vector3(.01f, .01f, difference.length()),
                                    Vector3.zero(), material);
                            /* Last, set the world rotation of the node to the rotation calculated earlier and set the world position to
                                   the midpoint between the given points . */

                            nodeForLine.setParent(node2);
                            nodeForLine.setRenderable(model);
                            nodeForLine.setWorldPosition(Vector3.add(point1, point2).scaled(.5f));
                            nodeForLine.setWorldRotation(rotationFromAToB);
                            nodeForLine.setEnabled(true);

                        }
                );
        nodeForLines.add(nodeForLine);
    }

    /**
     * 画出所有的连线
     */
    private void drawAllLines(ArrayList<AnchorNode> anchorNodes) {
        for (int i = 0; i < anchorNodes.size() - 1; i++) {
            drawLine(anchorNodes.get(i), anchorNodes.get(i + 1));
        }
    }

    /**
     * 标记出连线的长度
     */
    private void drawInfo(ArrayList<Node> nodeForLines, ArrayList<Float> distances) {
        if (nodeForLines.size() != distances.size()) {
            String txt = "出带问题了！";
            txt += "nodeForLines: " + nodeForLines.size() + " distances: " + distances.size();
            Toast.makeText(this, txt, Toast.LENGTH_SHORT).show();

        } else {
            for (int i = 0; i < nodeForLines.size(); i++) {
                Node info = new Node();
                info.setParent(nodeForLines.get(i));
                info.setEnabled(true);
                info.setLookDirection(new Vector3(0,-0.02f,0));
                //info.setWorldRotation(new Quaternion(0,1,0,270));
                //info.setLocalScale(new Vector3(0.5f,0.5f,0.5f));
                info.setLocalPosition(new Vector3(0.0f, 0.01f, 0.0f));
                int finalI = i;
                ViewRenderable.builder()
                        .setView(this, R.layout.info_view)
                        .build()
                        .thenAccept(
                                (renderable) -> {
                                    info.setRenderable(renderable);
                                    TextView textView = (TextView) renderable.getView();
                                    textView.setText(String.valueOf(distances.get(finalI)));
                                });
                infos.add(info);
            }
        }
    }



    /**
     * @param bmp 获取的bitmap数据
     * @param picName 自定义的图片名
     */
    public void saveBmp2Gallery(Bitmap bmp, String picName) {

        String fileName = null;
        //系统相册目录
        String galleryPath= Environment.getExternalStorageDirectory()
                + File.separator + Environment.DIRECTORY_DCIM
                +File.separator+"Camera"+File.separator;


        // 声明文件对象
        File file = null;
        // 声明输出流
        FileOutputStream outStream = null;

        try {
            // 如果有目标文件，直接获得文件对象，否则创建一个以filename为名称的文件
            file = new File(galleryPath, picName+ ".jpg");

            // 获得文件相对路径
            fileName = file.toString();
            // 获得输出流，如果文件中有内容，追加内容
            outStream = new FileOutputStream(fileName);
            if (null != outStream) {
                bmp.compress(Bitmap.CompressFormat.PNG, 90, outStream);
            }

        } catch (Exception e) {
            e.getStackTrace();
        }finally {
            try {
                if (outStream != null) {
                    outStream.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        MediaStore.Images.Media.insertImage(getContentResolver(), bmp, fileName, null);
        Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        Uri uri = Uri.fromFile(file);
        intent.setData(uri);
        sendBroadcast(intent);

        Toast.makeText(this, "完成保存！", Toast.LENGTH_SHORT).show();
    }


    /**
     * Returns false and displays an error message if Sceneform can not run, true if Sceneform can run
     * on this device.
     *
     * <p>Sceneform requires Android N on the device as well as OpenGL 3.0 capabilities.
     *
     * <p>Finishes the activity if Sceneform can not run
     */
    public static boolean checkIsSupportedDeviceOrFinish(final Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Log.e(TAG, "Sceneform requires Android N or later");
            Toast.makeText(activity, "Sceneform requires Android N or later", Toast.LENGTH_LONG).show();
            activity.finish();
            return false;
        }
        String openGlVersionString =
                ((ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE))
                        .getDeviceConfigurationInfo()
                        .getGlEsVersion();
        if (Float.parseFloat(openGlVersionString) < MIN_OPENGL_VERSION) {
            Log.e(TAG, "Sceneform requires OpenGL ES 3.0 later");
            Toast.makeText(activity, "Sceneform requires OpenGL ES 3.0 or later", Toast.LENGTH_LONG)
                    .show();
            activity.finish();
            return false;
        }
        return true;
    }

}

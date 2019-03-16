package com.example.a40136.arcore_measure;

import android.graphics.Bitmap;
import android.util.Log;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Iterator;

public class CornorDetect {

    private static final String TAG = "CornorDetect";

    public static ArrayList<Point> getCorner(Bitmap bitmap){


        // 原始图像
        Mat img = new Mat();
        Utils.bitmapToMat(bitmap,img);
        System.out.println("img = " + img);

        // 灰度图像
        Mat gray = new Mat();
        Imgproc.cvtColor(img,gray,Imgproc.COLOR_BGR2GRAY);
        System.out.println("gray = " + gray);


        // 模糊图像
        Mat blur = new Mat();
        Imgproc.bilateralFilter(gray, blur, 10, 100, 100);

        // 边缘检测
        Mat canny = new Mat();
        Imgproc.Canny(blur,canny,20,150);

        //角点检测
        Mat dst = new Mat();
        Imgproc.cornerHarris(canny, dst, 2, 3, 0.04);

        //得到角点
        Mat dst_norm = new Mat();
        Core.normalize(dst, dst_norm,0,255,Core.NORM_MINMAX, CvType.CV_32FC1,new Mat());

        Mat harrisCorner = new Mat();
        Imgproc.threshold(dst, harrisCorner, 0.00001, 255, Imgproc.THRESH_BINARY);

        // 提取角点坐标
        ArrayList<Point> points = new ArrayList<>();
        for (int i = 0; i < harrisCorner.rows(); i++) {
            for (int j = 0; j < harrisCorner.cols(); j++) {
                double[] temp = harrisCorner.get(i,j);
                for (double value : temp) {
                    if (value>0){
                        points.add(new Point(j, i));
                        System.out.println("row: "+i+"\tcol: "+j);
                        Log.d(TAG, "getCorner: "+ "row: "+i+"\tcol: "+j);
                    }
                }
            }
        }

        int gap = Math.min(img.rows(),img.cols()) / 2;
        System.out.println("gap = " + gap);
        System.out.println("points.size() = " + points.size());
        if (points.size()<5){
            return null;
        }else {
            points = getPoint(points);
            for (Point point: points) {
                System.out.println("point = " + point);
            }
            return points;
        }
    }


    private static ArrayList<Point> getPoint(ArrayList<Point> points) {
       /* points.add(new Point(0,0));
        points.add(new Point(0,1));
        points.add(new Point(1,0));
        points.add(new Point(0,100));
        points.add(new Point(0,101));
        points.add(new Point(1,100));
        points.add(new Point(100,0));
        points.add(new Point(100,1));
        points.add(new Point(101,0));
        points.add(new Point(100,100));
        points.add(new Point(100,101));
        points.add(new Point(101,100));*/

        Point z0 = points.get(0);
        Point z1 = null;
        Point z2 = null;
        Point z3 = null;

        z1 = getFarthest(z0, points);
        double T = distance(z0,z1) / 4;
        points = removePoints(z1, points,T);
        z2 = getFarthest(z0,points);
        points = removePoints(z2, points,T);
        z3 = getFarthest(z0,points);

        ArrayList<Point> results = new ArrayList<>();
        results.add(z0);
        results.add(z1);
        results.add(z2);
        results.add(z3);
        return results;
    }

    private static ArrayList<Point> removePoints(Point z, ArrayList<Point> points, double T){
        Iterator<Point> iterator = points.iterator();
        while (iterator.hasNext()){
            if (distance(iterator.next(),z)<T) iterator.remove();
        }
        return points;
    }

    private static Point getFarthest(Point z, ArrayList<Point> points){
        double max = 0;
        Point temp = null;
        for (Point point: points) {
            if (max < distance(z,point)){
                max = distance(z,point);
                temp = point;
            }
        }
        return temp;
    }

    private static double distance(Point p1, Point p2){
        double dx = p1.x-p2.x;
        double dy = p1.y - p2.y;
        return Math.sqrt(dx*dx+dy*dy);
    }
}

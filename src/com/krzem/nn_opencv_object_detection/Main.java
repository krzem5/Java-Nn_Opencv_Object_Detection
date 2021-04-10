package com.krzem.nn_opencv_object_detection;



import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.PrintWriter;
import java.lang.Exception;
import java.lang.Math;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;



public class Main{
	public static void main(String[] args){
		new Main(args);
	}



	private VideoCapture cap;
	private int[][] dpl;
	private double[][][] nn_wl;
	private double[][] nn_bl;
	private int _thr_left;
	private String _otp_fn;
	private long _otp_c;
	private List<Long> _otp_tm_l;
	private double _otp_s_tm;
	private long _otp_ns_tm;
	private boolean _otp_s_ch;



	public Main(String[] args){
		if (args.length<6){
			throw new IllegalArgumentException("Usage: java -jar main.jar <camera id|video file path> <nn model file path> <output file path> <output rate (seconds)> <square overlap> <sq 1 size>[,<sq 2 size>[,<sq 3 size>[,<sq N size>]]]");
		}
		long st=System.nanoTime();
		System.out.println("[APP]\tStarting to load OpenCV Library...");
		try{
			InputStream is=Main.class.getResourceAsStream("/com/krzem/nn_opencv_object_detection/modules/opencv_java420.dll");
			byte[] bf=new byte[1024];
			int t=-1;
			File t_f=File.createTempFile("opencv_java420.dll","");
			FileOutputStream os=new FileOutputStream(t_f);
			while ((t=is.read(bf))!=-1){
				os.write(bf,0,t);
			}
			os.close();
			is.close();
			System.load(t_f.getAbsolutePath());
		}
		catch (Exception e){
			e.printStackTrace();
			System.exit(1);
		}
		System.out.println("[APP]\tOpenCV Library loaded!\n[APP]\tStarting to read NN Model file...");
		Main cls=this;
		try{
			BufferedReader r=new BufferedReader(new FileReader(args[1]));
			String l=null;
			int s=0;
			int wi=0;
			int bi=0;
			while ((l=r.readLine())!=null){
				if (s==0){
					l=l.split(":")[1];
					int i=-1;
					int[] h=new int[l.length()-l.replace("x","").length()+1-2];
					int o=-1;
					int hi=0;
					int idx=0;
					for (String d:l.split("x")){
						if (idx==0){
							i=Integer.parseInt(d);
						}
						else if (idx==l.length()-l.replace("x","").length()){
							o=Integer.parseInt(d);
						}
						else{
							h[hi]=Integer.parseInt(d);
							hi++;
						}
						idx++;
					}
					System.out.printf("[NN]\tDetected NN Size: %s\n",l.replace("x"," x "));
					this.nn_wl=new double[h.length+1][][];
					this.nn_bl=new double[h.length+1][];
				}
				else if (s==1&&!l.equals("")){
					int mw=Integer.parseInt(l.split(":")[0].split("x")[0]);
					int mh=Integer.parseInt(l.split(":")[0].split("x")[1]);
					this.nn_wl[wi]=new double[mh][mw];
					System.out.printf("[NN]\tStarting to parse %d x %d Weight Matrix...\n",mw,mh);
					l=l.split(":")[1];
					this._thr_left=0;
					String[] _ll=l.split(";");
					for (int y=0;y<mh;y+=20){
						int sy=y+0;
						this._thr_left++;
						double[][] a=this.nn_wl[wi];
						String[] ll=_ll;
						new Thread(new Runnable(){
							@Override
							public void run(){
								for (int y=sy+0;y<Math.min(sy+20,mh);y++){
									String ln=ll[y];
									for (int x=0;x<mw;x++){
										a[y][x]=Double.parseDouble(ln.split(",")[x]);
									}
								}
								synchronized (cls){
									cls._thr_left--;
								}
							}
						}).start();
					}
					while (this._thr_left>0){
						try{
							Thread.sleep((int)(1000/30));
						}
						catch (Exception e){
							e.printStackTrace();
						}
					}
					wi++;
					System.out.printf("[NN]\t%d x %d Weight Matrix parsed!\n",mw,mh);
				}
				else if (s==2){
					int ms=Integer.parseInt(l.split(":")[0]);
					this.nn_bl[bi]=new double[ms];
					System.out.printf("[NN]\tStarting to parse %d x 1 Bias Matrix...\n",ms);
					l=l.split(":")[1];
					for (int x=0;x<ms;x++){
						this.nn_bl[bi][x]=Double.parseDouble(l.split(",")[x]);
					}
					bi++;
					System.out.printf("[NN]\t%d x 1 Bias Matrix parsed!\n",ms);
				}
				if (s==0){
					s=1;
				}
				else if (l.equals("")){
					s=2;
				}
			}
			r.close();
			System.out.println("[APP]\tNN Model loaded!\n[APP]\tOpening capture...");
		}
		catch (Exception e){
			e.printStackTrace();
			System.exit(1);
		}
		try{
			this.cap=new VideoCapture(Integer.parseInt(args[0]));
			System.out.printf("[CAPTURE]\tOpening capture on external camera #%d...\n",Integer.parseInt(args[0]));
		}
		catch (Exception e){
			this.cap=new VideoCapture(args[0]);
			System.out.printf("[CAPTURE]\tOpening capture on file %s...\n",args[0]);
		}
		System.out.printf("[CAPTURE]\tDetected input size: %dpx x %dpx\n[CAPTURE]\tDetected input frame rate: %ffps\n[APP]\tCapture opened!\n[APP]\tStarting to generate detection rectangles...\n",(int)this.cap.get(Videoio.CAP_PROP_FRAME_WIDTH),(int)this.cap.get(Videoio.CAP_PROP_FRAME_HEIGHT),this.cap.get(Videoio.CAP_PROP_FPS));
		int w=Math.min((int)this.cap.get(Videoio.CAP_PROP_FRAME_WIDTH),1920);
		double sq_o=1-Double.parseDouble(args[4]);
		int[] sq_sl=new int[args.length-5];
		int n=0;
		for (int i=5;i<args.length;i++){
			sq_sl[i-5]=Integer.parseInt(args[i]);
			n+=(int)Math.ceil(w/Math.floor(sq_sl[i-5]*sq_o));
		}
		this.dpl=new int[n][3];
		int k=0;
		for (int i=0;i<sq_sl.length;i++){
			for (int j=0;j<w;j+=(int)(sq_sl[i]*sq_o)){
				this.dpl[k]=new int[]{(j+sq_sl[i]>w?w-sq_sl[i]:j),0,sq_sl[i]};
				k++;
			}
		}
		System.out.printf("[APP]\t%d detection rectangles generated!\n[APP]\tStarting main thread...\n",k);
		this._otp_fn=args[2];
		this._otp_s_tm=Double.parseDouble(args[3]);
		this._otp_ns_tm=System.nanoTime()+(long)(this._otp_s_tm*1e9);
		this._otp_c=0;
		this._otp_tm_l=new ArrayList<Long>();
		new Thread(new Runnable(){
			@Override
			public void run(){
				while (true){
					cls._frame();
					try{
						Thread.sleep((int)(1000/60));
					}
					catch (Exception e){
						e.printStackTrace();
					}
				}
			}
		}).start();
		double dt=(double)(System.nanoTime()-st);
		System.out.printf("[APP]\tMain thread started!\n[APP]\tStartup took %.2f seconds.\n",dt*1e-9);
	}



	private void _frame(){
		Mat img=new Mat();
		this.cap.read(img);
		if (img.empty()==true){
			System.out.println("[CAPTURE]\tReached end of file!\n[APP]\tClosing Application...");
			System.exit(0);
		}
		if (this.cap.isOpened()==false){
			return;
		}
		if (img.width()>1920){
			Imgproc.resize(img.clone(),img,new Size(1920,1080));
		}
		Imgproc.cvtColor(img.clone(),img,Imgproc.COLOR_BGR2GRAY);
		Imgproc.GaussianBlur(img.clone(),img,new Size(7,7),0);
		MatOfDouble tmp=new MatOfDouble();
		this._thr_left=0;
		Main cls=this;
		for (int[] r:this.dpl){
			Mat sm=new Mat();
			Core.meanStdDev(img.submat(r[1],r[1]+r[2],r[0],r[0]+r[2]),tmp,new MatOfDouble());
			Imgproc.threshold(img.submat(r[1],r[1]+r[2],r[0],r[0]+r[2]),sm,tmp.get(0,0)[0]+23,255,Imgproc.THRESH_BINARY);
			Imgproc.resize(sm.clone(),sm,new Size(32,32));
			byte[] sq=new byte[1024];
			sm.get(0,0,sq);
			this._thr_left++;
			int i=this._thr_left-1;
			new Thread(new Runnable(){
				@Override
				public void run(){
					double[] a=null;
					int o=-1;
					double mx=-Double.MAX_VALUE;
					for (int j=0;j<cls.nn_wl.length;j++){
						double[] na=new double[cls.nn_wl[j][0].length];
						for (int k=0;k<cls.nn_wl[j][0].length;k++){
							if (a==null){
								for (int l=0;l<cls.nn_wl[j].length;l++){
									na[k]+=cls.nn_wl[j][l][k]*((sq[l]&0xff)==0?0:1);
								}
							}
							else{
								for (int l=0;l<cls.nn_wl[j].length;l++){
									na[k]+=cls.nn_wl[j][l][k]*a[l];
								}
							}
							na[k]=1/(1+Math.exp(-na[k]-cls.nn_bl[j][k]));
							if (j==cls.nn_wl.length-1&&na[k]>=mx){
								mx=na[k]+0;
								o=k+0;
							}
						}
						a=na;
					}
					synchronized (cls){
						cls._thr_left--;
						if (o==1){
							cls._otp_c++;
							cls._otp_tm_l.add(System.currentTimeMillis());
							cls._otp_s_ch=true;
						}
					}
				}
			}).start();
		}
		while (this._thr_left>0){
			try{
				Thread.sleep((int)(1000/60));
			}
			catch (Exception e){
				e.printStackTrace();
			}
		}
		if (this._otp_ns_tm-System.nanoTime()<=0){
			this._otp_ns_tm=System.nanoTime()+(long)(this._otp_s_tm*1e9);
			if (this._otp_s_ch==true){
				this._otp_s_ch=false;
				try{
					BufferedWriter w=new BufferedWriter(new FileWriter(this._otp_fn));
					w.write(Long.toString(this._otp_c));
					for (Long tm:this._otp_tm_l){
						w.write("\n"+tm.toString());
					}
					w.close();
				}
				catch (Exception e){
					e.printStackTrace();
				}
			}
		}
	}
}

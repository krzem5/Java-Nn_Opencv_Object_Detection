import os
import subprocess
import sys
import zipfile



if (os.path.exists("build")):
	dl=[]
	for r,ndl,fl in os.walk("build"):
		dl=[os.path.join(r,k) for k in ndl]+dl
		for f in fl:
			os.remove(os.path.join(r,f))
	for k in dl:
		os.rmdir(k)
else:
	os.mkdir("build")
jfl=[]
for r,_,fl in os.walk("src"):
	for f in fl:
		if (f[-5:]==".java"):
			jfl.append(os.path.join(r,f))
if (subprocess.run(["javac","-cp","src/com/krzem/nn_opencv_object_detection/modules/opencv-420.jar;","-d","build"]+jfl).returncode!=0):
	sys.exit(1)
with zipfile.ZipFile("build/nn_opencv_object_detection.jar","w") as zf,zipfile.ZipFile("src/com/krzem/nn_opencv_object_detection/modules/opencv-420.jar","r") as jf:
	print("Writing: META-INF/MANIFEST.MF")
	zf.write("manifest.mf",arcname="META-INF/MANIFEST.MF")
	print("Writing: com/krzem/nn_opencv_object_detection/modules/opencv_java420.dll")
	zf.write("src/com/krzem/nn_opencv_object_detection/modules/opencv_java420.dll",arcname="com/krzem/nn_opencv_object_detection/modules/opencv_java420.dll")
	for r,_,fl in os.walk("build"):
		for f in fl:
			if (f[-6:]==".class"):
				print(f"Writing: {os.path.join(r,f)[6:].replace(chr(92),'/')}")
				zf.write(os.path.join(r,f),os.path.join(r,f)[6:])
	for k in jf.namelist():
		if (k.upper()!="META-INF/MANIFEST.MF" and k[-6:]==".class"):
			dt=jf.read(k)
			if (len(k)>0):
				print(f"Writing: {k}")
				zf.writestr(k,dt)
if ("--run" in sys.argv):
	subprocess.run(["java","-jar","build/nn_opencv_object_detection.jar","D:/K/Project/project2/DATA/video/video-6.MOV","D:/K/Coding/Java-NN_Image_Recognition/data/full.nn-data","out.txt","0.01","0.5","80","40","30"])

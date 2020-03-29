echo off
echo NUL>_.class&&del /s /f /q *.class
cls
javac -cp com/krzem/nn_opencv_object_detection/modules/opencv-420.jar; com/krzem/nn_opencv_object_detection/Main.java&&jar -cfmv main.jar manifest.mf com/krzem/nn_opencv_object_detection/*
java -jar main.jar D:\K\Project\project2\DATA\video\video-6.MOV D:\K\Coding\projects\Java-NN_Image_Recognition\data\full.nn-data ./out.txt 0.01 0.5 80 40 30
start /min cmd /c "echo NUL>_.class&&del /s /f /q *.class"
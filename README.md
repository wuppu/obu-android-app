# obu-android-app
# How to Apply V2X in Environments Where GPS Use is Restricted
## Author
- Ju-Young Kim
  - dept. Electronic Engineering
  - Hanyang Univ. Seoul, Republic of Korea
  - wuppu1640@hanyang.ac.kr

- Tae-Kyung Kim
  - dept. Electronic Engineering
  - Hanyang Univ. Seoul, Republic of Korea
  - rlaxorud0331@hanyang.ac.kr

## Abstract
V2X provides important information to drivers through communication between vehicle and infrastructure. This information is aimed at the safety and convenience of drivers and helps to predict or respond quickly to emergency situations. And in order to prevent vehicle accidents, it is important to accurately position the vehicle, and infrastructure equipment must monitor the vehicle's location in real time. However, it is difficult to accurately specify the location of a vehicle in an area where GPS signals are not received, such as in a tunnel. In the GPS shadow area, it generally relies on the IMU sensor to calculate the movement path. However, when only the IMU sensor is used, an error occurs and the value accumulates, resulting in a large difference from the actual value. Therefore, the paper devised a method to calculate the movement route with the IMU sensor, correct the value of the IMU sensor through GPS and RSU information, and increase the accuracy of the movement route.

## Introduction
The C-ITS(Cooperative Intelligent Transportation System) service can provide more diverse and effective services using V2X(Vehicle-to-Everything) communication based on WAVE(Wireless Access in Vehicular Environments) technology. In Korea, research on V2X communication technology has been conducted through smart highway research, and a test bed is being built and operated to verify the technical performance in an actual highway environment \cite
b1}. In the United States, it is installed and used on actual roads, and additional installation is planned[2]. Services provided include collision avoidance, emergency vehicle approach warning, and provision of road condition and weather information. RSU(Road-Side Unit) and infrastructure equipment must monitor the vehicle's location in real-time to know vehicle accidents and the approach of emergency vehicles. This is because accurate information can be delivered to the driver based on the real-time location of vehicles.

![background_info](https://user-images.githubusercontent.com/26536939/174234777-fd0255ea-7ea4-44d9-aa60-2cbe9d14dde2.png)

In V2X communication, location information must be included and transmitted through the GPS(Global Positioning System). On general roads, there is no problem in collecting and transmitting location information because the GPS signal is well captured. However, it is difficult to specify a location in a terrain where GPS signals are difficult to receive, such as a tunnel. In particular, vehicle location information in the tunnel is more important. The visibility inside the tunnel is narrower than that of a normal road, and it is difficult to know the situation in front of the road, and it is not easy to visually determine the current location. Therefore, since it is more difficult to deal with a vehicle accident inside a tunnel than on a general road, it is necessary to specify the location of the vehicle and apply V2X communication suitable for it.

![approach](https://user-images.githubusercontent.com/26536939/174234866-10a230f8-f4a6-4177-94d8-268c001668f5.png)

Through the IMU(Inertial Measurement Unit) sensor inside the vehicle, the vehicle's movement route data was calculated and utilized. However, when only the IMU sensor is used, an error occurs in the process of calculating the moving distance, and this error is accumulated, and the moving path of the vehicle makes a big difference from the actual moving path[3].

The RSU installed inside the tunnel is used by the administrator setting the reference coordinates. Reference coordinates mean coordinates on the map, and since GPS signals are not captured inside the tunnel, the value entered by the administrator is used instead of the GPS coordinate value.

A method was devised to correct the error of the movement path by using the reference coordinates of the RSU installed at regular intervals in the tunnel movement path. In this method, the reference coordinates possessed by the RSU are transmitted to the OBU( On-Board Unit) installed in the vehicle, and the OBU uses the reference coordinates to correct its own position. If this method is used, it is possible to solve the problem of deviation from the movement path due to the error generated by the IMU.

For example, if the location information of the vehicle can be specified by using the RSU in the situation inside the tunnel, it can be applied to the terrain where the GPS signal is weak or impossible. In particular, it can be used in the group that is often exposed to such situations. Most of the military relay stations are located in difficult mountainous areas, and there are many areas where radio and GPS reception are restricted on the road section leading up to the communication station. In addition, roads may be lost depending on the weather, making it difficult to specify the location.

By making it possible to accurately determine the location information of these general drivers and specific people, it can be possible to cope with collisions, emergencies, and dangerous situations.

## Related Work
Various studies and attempts are continuing to increase the accuracy of location information in tunnels. Research using LiDAR(Light Detection and Ranging), INS(Initial Navigation System), and IMU[3][4]. The LiDAR sensor may be installed inside the vehicle and the tunnel to determine the position of the vehicle even in an invisible situation. The IMU's gyroscope, accelerometer, and geomagnetic sensors allow continuous calculations and estimation of locations, directions, and speeds without external reference information.

![Untitled Diagram drawio](https://user-images.githubusercontent.com/26536939/174235002-035a5dcf-432e-4d36-b123-bee8c9105ada.png)

In addition, in Korea, a system that can guess the lane and location of a vehicle using different LED lights and cameras was studied based on the problem that data is emitted due to the accumulation of IMU errors over time[5]. This is the content that the location of the current lane of the vehicle can be estimated by analyzing the color temperature of the LED lighting. You can see this in Fig. 3.

In order to overcome the discontinuity of GPS and the limitation of speed tracking in the receiving shadow area, there are also papers that propose a system for estimating the speed of a moving object using an accelerometer and GPS[6].  By receiving GPS receiver and accelerometer navigation information, the vibration and shock of the object and noise caused by the error of the accelerometer itself are corrected through Kalman filter, and it is designed to supplement the speed estimation of the moving object and discontinuity of GPS navigation information.

![triangulation](https://user-images.githubusercontent.com/26536939/174235083-83c47587-f178-416c-9a6e-7429b965762a.png)

In order to estimate the location in the GPS shadow area, there is a paper using the method of estimating the location of a vehicle through inter-vehicle triangulation, RSSI(Radio Signal Strength Information), and TDOA(Time Difference Of Arrival)[7]. The assumption is this. There is only a vehicle inside the tunnel, and communication is made at a straight-line distance between the vehicle and the vehicle, enabling smooth communication without interference. If the vehicle has a WLAN(Wireless LAN) device and there are three or more vehicles, the location data can be obtained by triangulation by measuring the RSSI of vehicle-to-vehicle communication. And it is a method that can obtain the vehicle's location by moving while obtaining the vehicle's speed data from this data.

As such, various studies have been conducted, and it can be seen that not only one technology is used, but various technologies are synthesized and calculated. Therefore, we are trying to build a system that provides data that can be used with these technologies.

## Approach
An attempt was made to implement a method of measuring the RSSI between the OBU and the RSU of the existing method and converting it into distance data. However, as a result of direct testing, it was confirmed that the RSSI value changes easily due to obstacles. Therefore, it was difficult to understand the correlation between RSSI and distance data, so a different approach was attempted.

A method was considered to solve the problem by setting fixed reference coordinates in the RSU installed in the tunnel where the existing GPS signal cannot be used. RSUs are installed in the tunnel at regular intervals, and fixed coordinate values measured in advance are set in the RSU and transmitted to the OBU so that the OBU can estimate its location. And the movement path of the OBU will be calculated using the IMU sensor of the OBU. However, the movement path may be different from the actual movement path due to an error occurring in the accelerometer of the IMU. Therefore, the error occurring in the accelerometer will be corrected using the reference coordinates of the RSU.

In this section, the implementation process of the project, the method of obtaining the movement path with the accelerometer, and the method of compensating it will be described in a procedural manner.

### A. Message Exchange Procedures and Format Specifications
Before developing the program, the specifications for the procedure and format of messages to be used for communication between OBU and RSU were summarized. The RSU transmits the reference coordinates measured in advance at regular intervals. The OBU stores the received reference coordinates and according to the settings, includes GPS, accelerometer, or corrected data in a message and transmits it to the RSU at regular intervals.

### B. Bluetooth Communication
![image](https://user-images.githubusercontent.com/26536939/174236158-6aa11743-8d21-4119-b830-46a7c8f848d9.png)

Communication between OBU and RSU was implemented using Bluetooth instead of WAVE communication. The equipment used was Android and Windows PC, and the language was JAVA and C language. Since Android has built-in GPS, IMU and Bluetooth modules, it was developed as OBU because it is similar to devices built into vehicles. Since RSU does not require specific functions, a labtop equipped with a Bluetooth module was used and implemented in C language to handle data transmission and reception, data storage, and data visualization.

### C. How To Measure Android Accelerometer
![device_world](https://user-images.githubusercontent.com/26536939/174236280-42781fdd-5d57-4f4f-89a6-b22ba1c4be3a.png)

When measuring an acceleration value on an Android device, the data is output based on the device. However, device-based acceleration values cannot be used as coordinate data. So, we need to convert the device acceleration values into Earth-based data.

![rotation matrix](https://user-images.githubusercontent.com/26536939/174236336-d001d972-51b1-4f28-9e27-180bc290c7bd.png)

In this case, the rotation matrix can be obtained from the data of the acceleration sensor, the geomagnetic sensor, and the gravity sensor. And, by multiplying the rotation matrix with the existing device accelerometer data, it is possible to obtain an earth-based accelerometer value.

### D. Calculation of Acceleration as Moving Distance and Error
![integral_error](https://user-images.githubusercontent.com/26536939/174236479-526397a1-1768-4aeb-b4ee-898ee5f7d966.png)

The error value is generated by the noise generated by the accelerometer, and abnormal data that appears due to vibration or collision. Therefore, it is necessary to correct the data through the Kalman filter for values caused by noise or vibration[6]. In addition, double integration is required to express the accelerometer values as coordinates. In this double integration process, an error is also generated. Since the length of the x-axis cannot be infinitely reduced during the integration process, a difference appears in the area. Due to this, there is a problem that the movement path does not come out in a desired direction as time goes by.

### E. Error Correction
![correction](https://user-images.githubusercontent.com/26536939/174236566-8af2899b-9aa6-4043-a2e1-230deff5ca60.png)

Currently, the OBU knows the current coordinates calculated by its last GPS, RSU and accelerometers. Since we know the coordinates of the three points, we can know how far the current coordinates calculated by the accelerometer are deviated from the original direction. Coordinate transformation through Euler Angle Rotation, or a circle and a straight line, coordinates can be corrected through contact points.

## Evaluation
The format and procedure of the message required for communication were defined, and the functions of Bluetooth communication, GPS, and IMU sensor measurement, which are some of the main functions, were implemented and tested. And through the test, GPS, IMU, and calibration data were measured and outputted as maps and graphs. The test proceeded to measure the linear distance at a speed of about 20 km/h, and the measurement period was 100 milliseconds.

### A. Testing Using GPS Only
![gpsonly](https://user-images.githubusercontent.com/26536939/174236807-4c34f2cc-3809-4e97-9594-5e154a12dcf5.png)

It is a record of receiving and storing the actual GPS data for comparison with the corrected data. As shown in the figure, a straight road was measured by driving at a constant speed. The GPS measurement period is 100 milliseconds, which was received and stored by the RSU.

### B. Testing Using IMU Sensor Only
![acconly](https://user-images.githubusercontent.com/26536939/174236821-52b3cbe0-4267-4e57-ba87-a758bba79559.png)

It is a record of receiving and storing the IMU sensor data without correction in order to compare it with the corrected data. It can be seen that the direction of movement is significantly different from the record using only GPS data. It can be seen that an error occurs at the beginning and this error continues to accumulate. Also, the performance problem of the sensor seems to be the cause of this error.

### C. Calibrated IMU Sensor Test
![ref](https://user-images.githubusercontent.com/26536939/174236835-5d196897-9643-483d-98e6-3ce030165be0.png)

It is a record that actually stores the corrected data. Before starting the measurement, GPS was received, the last GPS value was saved, and the data of the IMU sensor was corrected in the state that GPS reception was terminated. As expected, it can be seen that the coordinates are corrected with a straight line. However, when looking at the interval of 100 milliseconds of the measured coordinates, it was confirmed that the interval was larger than that of the GPS data. This seems to have occurred in the speed data measured by the IMU.

### D. Result Graph
![coordinate_comparison](https://user-images.githubusercontent.com/26536939/174236874-6933cda6-2ef7-4f89-84fa-6278c616e0f4.png)

The figure above shows the coordinates of three data in a graph. ***obu_reference*** expressed the coordinates of the calibrated IMU sensor, ***obu_acconly*** expressed the coordinates of the IMU sensor, and ***obu_gpsonly*** expressed the coordinates of the GPS. If you look at the graph, you can see that ***obu_reference*** and ***obu_gpsonly*** moved along a similar movement path. However, in obu_acconly, you can see that the data error occurs and the movement direction is changed.

Correcting the movement path by calibrating the value of the IMU sensor is successful. However, although the movement direction was corrected, it is necessary to introduce additional correction or a new measurement method for the speed. The reason for the large error in the test seems to be the noise generated by the accelerometer, the bounce of values due to vibration, and the performance problem of the sensor.

## Conclusions
The project was switched to a method that uses IMU sensor values instead of using RSSI data that was previously intended to be used. I think it was well applied using new data so that the direction of the project did not change significantly. It wasn't perfect, but the results were successful as the expected data was output.

Currently, since the project was carried out assuming a straight road, it is difficult to output accurate result data in the case of a curved road. To compensate for this, it is necessary to study and implement a case where there is a curve. In addition, in order to accurately calculate the vehicle speed, additional planning is essential to derive a more accurate value by introducing an additional correction method or using a new sensor.

## References
[1] Jung H.G., Lim K.T., Shin D.K., Yoon S.H., Jin S.K., Jang S.H., and Shin J.S., "Experimental Analysis of V2X Communication Performance based on WAVE at the SMART-Highway Test-bed," J. Korea Inst. Intell. Transp. Syst., vol. 15, no. 4, (2016), pp. 115-128.

[2] “Operational connected vehicle deployments in the U.S.,” U.S. Department of Transportation. [Online]. Available: https://www.transportation.gov/research-and-technology/operational-connected-vehicle-deployments-us. [Accessed: 15-Jun-2022]. 

[3] Kong S. H., Jeon S. Y., and Ko H. W.(2015), ”Status and trends in the sensor fusion positioning technology,” The Journal of The Korean Institute of Communication Sciences, vol. 32, no. 8, pp.45-53.

[4] B. Wang, D. Sun, H. Zu, C. Wu, D. Zhang, and X. Chen, “Fusion positioning system based on IMU and roadside lidar in tunnel for C- V2X use,” SAE Technical Paper Series, 2020.

[5] Jeong J. H., Lee D. H., Byun G. S., Cho H. R., and Cho Y. H., “The Tunnel Lane Positioning System of a Autonomous Vehicle in the LED Lighting,” The Journal of The Korea Institute of Intelligent Transport Systems, vol. 16, no. 1. The Korea Institute of Intelligent Transport Systems, pp. 186–195, 28-Feb-2017.

[6] Yeom J.N., Lee G.B., Park J.M., and Jo B.J., "Speed Estimation of Moving Object using GPS and Accelerometer," Journal of Korea Multimedia Society, vol. 12, no. 4, (2009), pp. 600-607.

[7] Sun W.S., Sim S., Cho S.Y., Lim H.J., Chung T.M., "Wireless Lan Inter-Vehicle Communication Protocol for Collision Avoidance in GPS Receiving Restricted Area," Proceedings of the Korea Information Processing Society Conference, vol. 17, no. 2, (2010), pp. 1788-1791.

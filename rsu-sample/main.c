#include <unistd.h>
#include <fcntl.h>
#include <assert.h>
#include <termio.h>
#include <string.h>
#include <unistd.h>
#include <stdint.h>
#include <netinet/in.h>
#include <stdio.h>
#include <stdlib.h>
#include <pthread.h>
#include <stdbool.h>
#include <math.h>

#define PI 3.14159265357869323846
#define STR_MAX_SIZE 256
#define UNAVAILABLE_LATITUDE 900000001
#define UNAVAILABLE_LONGITUDE 1800000001
#define DEFAULT_TX_INTERVAL 100
#define REF_MESSAGE_LEN 20
#define ALERT_MESSAEG_LEN 20
#define NOTI_MESSAGE_LEN 24

#define STR_FAIL            "\x1b[1;31mFAIL\x1b[0m"
#define STR_ERRO            "\x1b[1;31mERRO\x1b[0m"
#define STR_OK              "\x1b[1;32m OK \x1b[0m"
#define STR_INFO            "\x1b[0mINFO\x1b[0m"
#define STR_WARN            "\x1b[1;33mWARN\x1b[0m"
#define STR_DEBUG           "\x1b[1;35mDBUG\x1b[0m"
#define STR_SEND            "\x1b[1;34mSEND\x1b[0m"
#define STR_RECV            "\x1b[1;33mRECV\x1b[0m"

/**
 * @brief 관리 구조체
 * */
struct MIB
{
  int32_t ref_latitude;
  int32_t ref_longitude;
  int32_t interval;
  char device_name[STR_MAX_SIZE];
  char output_file[STR_MAX_SIZE];
  int dbg_level;
};

/**
 * @brief 참조 좌표 설정 메시지
 * */
struct RefMessage
{
  int8_t id[4];
  uint32_t type;
  uint32_t body_len;
  int32_t latitude;
  int32_t longitude;
} __attribute__((__packed__));


/**
 * @brief 경고 메시지
 * */
struct AlertMessage
{
  int8_t id[4];
  uint32_t type;
  uint32_t body_len;
  int32_t latitude;
  int32_t longitude;
} __attribute__((__packed__));


/**
 * @brief 알림 메시지
 * */
struct NotiMessage
{
  int8_t id[4];
  uint32_t type;
  uint32_t body_len;
  int32_t latitude;
  int32_t longitude;
  int32_t rssi;
} __attribute__((__packed__));


/**
 * @brief 메시지 유형
 * */
enum eMessageType
{
  kMessageType_Ref = 1,
  kMessageType_Alert,
  kMessageType_Noti,
};
typedef int MessageType;


/**
 * @brief 디버그 레벨0
 * */
enum eDebugLevel
{
  kDebugLevel_None = 0,
  kDebugLevel_Error,
  kDebugLevel_Event,
  kDebugLevel_Info,
  kDebugLevel_Dump,
};

/* 전역 변수 */
int fd;
pthread_t tx_thread;
pthread_t rx_thread;
struct MIB g_mib;
FILE *fp;

/**
 * @brief 사용법 출력
 * */
void Usage(char *app) {
  printf("\n\n");
  printf(" Description: Simple Road-side unit application\n");
  // printf(" Version: %s\n", _VERSION_);
  printf(" Author: wuppu\n");
  printf(" Email: wuppu1640@hanyang.ac.kr\n");

  printf("\n");
  printf(" [USAGE]\n");
  printf(" %s <OPTIONS>\n", app);  
  printf(" --dev <device_name>              Serial port device name.\n");
  printf(" --file <output_file>             Notification save file path. If not specified, set to noti_save.csv.\n");
  printf(" --lat <ref_latitude>             Reference latitude. If not specified, set to 900000001.\n");
  printf(" --lng <ref_longitude>            Referfence longitude. if not specified, set to 1800000001.\n");
  printf(" --interval <tx_interval (mesc)>  Transmit interval(tx_interval >= 100 msec). If not specified, set to 100 msec.\n");
  printf(" --dbg <dbg_level>                Print log level. If not specified, set to 1.\n");
  printf("     0: None, 1: Error, 2: Event, 3: Info, 4: Dump\n");
  printf("\n\n");
}

/**
 * @brief 소수점 도(decimal degree)를 라디언(radian)으로 변환한다.
 * @param[in] deg 변환할 도 값
 * @return 변환한 라디언 값
 * */
double ConvertDecimalDegreesToRadians(double deg)
{
  return (deg * PI / 180);
}

/**
 * @brief 라디언(radian)을 소수점 도(decimal degree)로 변환한다.
 * @param[in] rad 변환할 라디언 값
 * @return 변환된 도 값
 * */
double ConvertRadiansToDeimalDegrees(double rad)
{
  return (rad * 180 / PI);
}

/**
 * @brief 두 좌표의 거리(미터단위)를 계산하여 반환한다.
 * @param[in] lat1 좌표1의 위도(도 단위)
 * @param[in] lon1 좌표1의 경도(도 단위)
 * @param[in] lat2 좌표2의 위도(도 단위)
 * @param[in] lon2 좌표2의 경도(도 단위)
 * @return 두 좌표간 거리(미터 단위)
 * */
double GetDistanceBetweenPoints(double lat1, double lon1, double lat2, double lon2)
{
  double theta, dist;
  if ((lat1 == lat2) && (lon1 == lon2)) {
    return 0;
  }
  else {
    theta = lon1 - lon2;
    dist = sin(ConvertDecimalDegreesToRadians(lat1)) * sin(ConvertDecimalDegreesToRadians(lat2)) +
           cos(ConvertDecimalDegreesToRadians(lat1)) * cos(ConvertDecimalDegreesToRadians(lat2)) * 
           cos(ConvertDecimalDegreesToRadians(theta));
    dist = acos(dist);
    dist = ConvertRadiansToDeimalDegrees(dist);
    dist = dist * 60 * 1.1515;
    dist = dist * 1.609344 * 1000; // 미터 단위 변환
    return dist;
  }
}

/**
 * @brief 입력 파라미터 처리
 * @param [in] argc 파라미터 개수
 * @param [in] argv 파라미터 내용물
 * */
int ProcessingInputParameter(int argc, char *argv[]) {
  g_mib.device_name[0] = '\0';
  strcpy(g_mib.output_file, "noti_save.csv");
  g_mib.ref_latitude = UNAVAILABLE_LATITUDE;
  g_mib.ref_longitude = UNAVAILABLE_LONGITUDE;
  g_mib.interval = DEFAULT_TX_INTERVAL;
  g_mib.dbg_level = kDebugLevel_Error;

  for (int i = 0; i < argc; i++) {
    // output_file
    if (strcmp(argv[i], "--file") == 0) {
      if (i + 1 < argc) {
        strcpy(g_mib.output_file, argv[i + 1]);
      }
    }

    // device_name
    if (strcmp(argv[i], "--dev") == 0) {
      if (i + 1 < argc) {
        strcpy(g_mib.device_name, argv[i + 1]);
      }
    }

    // ref_latitude
    if (strcmp(argv[i], "--lat") == 0) {
      if (i + 1 < argc) {
        g_mib.ref_latitude = (int32_t)atoi(argv[i + 1]);
      }
    }

    // ref_longitude
    if (strcmp(argv[i], "--lng") == 0) {
      if (i + 1 < argc) {
        g_mib.ref_longitude = (int32_t)atoi(argv[i + 1]);
      }
    }

    // interval
    if (strcmp(argv[i], "--interval") == 0) {
      if (i + 1 < argc) {
        g_mib.interval = (int32_t)atoi(argv[i + 1]);
      }
    }

    // dbg_level
    if (strcmp(argv[i], "--dbg") == 0) {
      if (i + 1 < argc) {
        g_mib.dbg_level = atoi(argv[i + 1]);
      }
    }
  }

  if (g_mib.device_name[0] == '\0') {
    printf("[%s] Wrong input parameter - device_name: NULL\n", STR_FAIL);
    return -1;
  }
  if (g_mib.ref_latitude > UNAVAILABLE_LATITUDE || g_mib.ref_latitude < -UNAVAILABLE_LATITUDE) {
    printf("[%s] Wrong input parameter - ref_latitude: %d\n", STR_FAIL, g_mib.ref_latitude);
    return -1;
  }
  if (g_mib.ref_longitude > UNAVAILABLE_LONGITUDE || g_mib.ref_longitude < -UNAVAILABLE_LONGITUDE) {
    printf("[%s] Wrong input parameter - ref_longitude: %d\n", STR_FAIL, g_mib.ref_longitude);
    return -1;
  }
  if (g_mib.interval < 100) {
    printf("[%s] Wrong input parameter - interval: %d\n", STR_FAIL, g_mib.interval);
    return -1;
  }

  // dump
  if (g_mib.dbg_level >= kDebugLevel_Dump) {
    printf("[%s] input parameter: \n", STR_DEBUG);
    printf("  device_name: %s\n", g_mib.device_name);
    printf("  ref_latitude: %d\n", g_mib.ref_latitude);
    printf("  ref_longitude: %d\n", g_mib.ref_longitude);
    printf("  interval: %d\n", g_mib.interval);
  }
  
  return 0;
}


/**
 * @brief 시리얼 포트 초기화
 * @param [in] device_name 연결할 시리얼 포트 디바이스 이름
 * @retval 음수: 실패
 * @retval 0: 성공
 * */
int SerialPortInit(char *device_name) {
  // Open the serial port
  fd = open(device_name, O_RDWR | O_NOCTTY);

  // Valid check
  if (fd < 0) {
    if (g_mib.dbg_level >= kDebugLevel_Error)
      printf("[%s] Fail to open serial port - device_name: %s\n", STR_FAIL, g_mib.device_name);
    return -1;
  }

  // Initialize the serial port
  struct termios newtio;
  memset(&newtio, 0, sizeof(struct termios));
  newtio.c_cflag = B9600 | CS8 | CLOCAL | CREAD;
  newtio.c_iflag = IGNPAR | ICRNL;
  newtio.c_oflag = 0;
  newtio.c_lflag = ~(ICANON | ECHO | ECHOE | ISIG);

  tcflush(fd, TCIFLUSH);
  tcsetattr(fd, TCSANOW, &newtio);

  return 0;
}


/**
 * @brief Notification 정보를 저장할 파일 초기화
 * @param [in] path 파일 경로 및 이름
 * @retval 음수: 실패
 * @retval 0: 성공
 * */
int NotiFileInit(char *path) {

  // 파일 쓰기로 열기
  fp = fopen(path, "w");
  if (fp == NULL) {
    if (g_mib.dbg_level >= kDebugLevel_Error)
      printf("[%s] Fail to open the file - path: %s\n", STR_FAIL, path);
    return -1;
  }

  // Header 입력
  fprintf(fp, "index, rsu_latitude, rsu_longitude, obu_latitude, obu_longitude, distance, rssi\n");
  return 0;
}


/**
 * @brief 메시지 송신 스레스 프로세스
 * @param [in] data unused
 * */
void *ProcessingTxMessage(void *data) {
  (void *)data;

  while (true) {
    usleep(g_mib.interval * 1000);

    // Create send message
    struct RefMessage ref;
    ref.id[0] = 'H';
    ref.id[1] = 'Y';
    ref.id[2] = 'E';
    ref.id[3] = 'S';
    ref.type = 1;
    ref.body_len = 8;
    ref.latitude = g_mib.ref_latitude;
    ref.longitude = g_mib.ref_longitude;

    // transmit send message
    int send_len = write(fd, &ref, sizeof(struct RefMessage));
    if (send_len < 0) {
      if (g_mib.dbg_level >= kDebugLevel_Error)
        printf("[%s] Fail to send the message - ret: %d\n", STR_FAIL, send_len);
      continue;
    }
    else {
      if (g_mib.dbg_level >= kDebugLevel_Event)
        printf("[%s] Success to send the Ref message - send_len: %d\n", STR_SEND, send_len);
      if (g_mib.dbg_level >= kDebugLevel_Info) {
        printf("[%s] RefMessage: \n", STR_INFO);
        printf("  id: ");
        for (unsigned int i = 0; i < sizeof(ref.id); i++) printf("%02X ", ref.id[i]);
        printf("("); for (unsigned int i = 0; i < sizeof(ref.id); i++) printf("%c", ref.id[i]); printf(")\n");
        printf("  type: %d (%s)\n", ref.type, ref.type == kMessageType_Ref ? "Ref" : "NULL");
        printf("  body_len: %u\n", ref.body_len);
        printf("  latitude: %d\n", ref.latitude);
        printf("  longitude: %d\n", ref.longitude);
      }
    }

    // Print send message dump
    if (g_mib.dbg_level >= kDebugLevel_Dump) {
      unsigned char *ptr = (unsigned char *)&ref;
      printf("[%s] send_len: %zu\n", STR_DEBUG, sizeof(struct RefMessage));
      printf("[%s] send: \n", STR_DEBUG);
      for (int i = 0; i < sizeof(struct RefMessage); i++) {
        if (i != 0 && i % 8 == 0) {
          if (i != 0 && i % 16 == 0) printf("\n"); 
          else printf(" "); 
        }
        if (i % 16 == 0) printf("  %06X ", i); 
        printf("%02X ", *(ptr + i));
      }
      printf("\n");
    }
  }
  
}


/**
 * @brief 메시지 수신 스레드 프로세스
 * @param [in] data unused
 * */
void *ProcessingRxMessage(void *data) {
  (void *)data;

  int noti_rx_cnt = 0;
  struct AlertMessage *alert = NULL;
  struct NotiMessage *noti = NULL;

  // rx message buffer
  unsigned char buf[1000];
  
  // initialize rx message buffer
  memset(buf, 0x00, sizeof(buf));

  // receive message loop
  while (1) {

    // Read the serial port
    int recv_len = read(fd, buf, sizeof(buf));

    // recv_len < 0 is fail to read the serial port
    if (recv_len < 0) {
      if (g_mib.dbg_level >= kDebugLevel_Error)
        printf("[%s] Fail to read the message - ret: %d\n", STR_FAIL, recv_len);
      continue;
    }
    else {
      noti = (struct NotiMessage *)buf;
      
      if (noti->type == kMessageType_Alert) {
        if (g_mib.dbg_level >= kDebugLevel_Event)
          printf("[%s] Success to read the Alert message - recv_len: %d\n", STR_RECV, recv_len);
        if (g_mib.dbg_level >= kDebugLevel_Info) {
          alert = (struct AlertMessage *)buf;
          printf("[%s] AlertMessage: \n", STR_INFO);
          printf("  id: ");
          for (unsigned int i = 0; i < sizeof(alert->id); i++) printf("%02X ", alert->id[i]);
          printf("("); for (unsigned int i = 0; i < sizeof(alert->id); i++) printf("%c", alert->id[i]); printf(")\n");
          printf("  type: %d (%s)\n", alert->type, alert->type == kMessageType_Alert ? "Alert" : "NULL");
          printf("  body_len: %u\n", alert->body_len);
          printf("  latitude: %d\n", alert->latitude);
          printf("  longitude: %d\n", alert->longitude);
        }
      }
      else if (noti->type == kMessageType_Noti) {
        noti_rx_cnt++;
        if (g_mib.dbg_level >= kDebugLevel_Event) 
          printf("[%s] Success to read the Noti message - recv_len: %d\n", STR_RECV, recv_len);
        if (g_mib.dbg_level >= kDebugLevel_Info) {
          printf("[%s] NotiMessage: \n", STR_INFO);
          printf("  id: ");
          for (unsigned int i = 0; i < sizeof(noti->id); i++) printf("%02X ", noti->id[i]);
          printf("("); for (unsigned int i = 0; i < sizeof(noti->id); i++) printf("%c", noti->id[i]); printf(")\n");
          printf("  type: %d (%s)\n", noti->type, noti->type == kMessageType_Noti ? "Noti" : "NULL");
          printf("  body_len: %u\n", noti->body_len);
          printf("  latitude: %d\n", noti->latitude);
          printf("  longitude: %d\n", noti->longitude);
          printf("  rssi: %d\n", noti->rssi);
        }
        
        // 계산식 사용
        double dist = GetDistanceBetweenPoints(g_mib.ref_latitude / 10000000,
                                               g_mib.ref_longitude / 10000000,
                                               noti->latitude / 10000000,
                                               noti->longitude / 10000000);
        // double dist_lat = pow((double)(g_mib.ref_latitude - noti->latitude) / 10000000, 2);
        // double dist_lng = pow((double)(g_mib.ref_longitude - noti->longitude) / 10000000, 2);
        // int distance = (int)(sqrt(dist_lat + dist_lng) * 1000);
        // 파일에 정보 입력
        fprintf(fp, "%d, %d, %d, %d, %d, %lf, %d\n", noti_rx_cnt, g_mib.ref_latitude, g_mib.ref_longitude, noti->latitude, noti->longitude, dist, noti->rssi);
      }
    }

    if (g_mib.dbg_level >= kDebugLevel_Dump) {
      // Print received message dump
      printf("[%s] recv_len: %d\n", STR_DEBUG, recv_len);
      printf("[%s] recv: \n", STR_DEBUG);
      for (int i = 0; i < recv_len; i++) { 
        if (i != 0 && i % 8 == 0) {
          if (i != 0 && i % 16 == 0) printf("\n");
          else printf(" "); 
        }
        if (i % 16 == 0) printf("  %06X ", i); 
        printf("%02X ", buf[i]);
      }
      printf("\n");
    }
  }    
}


int main(int argc, char *argv[]) {
  int ret;

  if (argc < 3) {
    Usage(argv[0]);
    exit(0);
  }

  ret = ProcessingInputParameter(argc, argv);
  if (ret < 0) {
    if (g_mib.dbg_level >= kDebugLevel_Error)
      printf("[%s] Fail to process input parameters\n", STR_FAIL);
    exit(0);
  }
  else {
    if (g_mib.dbg_level >= kDebugLevel_Event)
      printf("[%s] Success to process input parameters\n", STR_OK);
  }

  // 시리얼 포트 초기화
  ret = SerialPortInit(g_mib.device_name);
  if (ret < 0) {
    if (g_mib.dbg_level >= kDebugLevel_Error)
      printf("[%s] Fail to initialize the serial port - device_name: %s\n", STR_FAIL, g_mib.device_name);
    exit(0);
  }
  else {
    if (g_mib.dbg_level >= kDebugLevel_Event)
      printf("[%s] Success to initialize the serial port - device_name: %s\n", STR_OK, g_mib.device_name);
  }

  // 파일 입력 초기화
  ret = NotiFileInit(g_mib.output_file);
  if (ret < 0) {
    if (g_mib.dbg_level >= kDebugLevel_Error) 
      printf("[%s] Fail to initialize the notification file - output_file: %s\n", STR_FAIL, g_mib.output_file);
    exit(0);
  }
  else {
    if (g_mib.dbg_level >= kDebugLevel_Event)
      printf("[%s] Success to initialize the notification file - output_file: %s\n", STR_OK, g_mib.output_file);
  }

  // 수신 스레드 시작
  ret = pthread_create(&rx_thread, NULL, ProcessingRxMessage, NULL);
  if (ret < 0) {
    if (g_mib.dbg_level >= kDebugLevel_Error)
      printf("[%s] Fail to create processing rx message thread\n", STR_FAIL);
    exit(0);
  }
  else {
    if (g_mib.dbg_level >= kDebugLevel_Event)
      printf("[%s] Success to create processing rx message thread\n", STR_OK);
  }

  // 송신 스레드 시작
  ret = pthread_create(&tx_thread, NULL, ProcessingTxMessage, NULL);
  if (ret < 0) {
    if (g_mib.dbg_level >= kDebugLevel_Error)
      printf("[%s] Fail to create processing tx message thread\n", STR_FAIL);
    exit(0);
  }
  else {
    if (g_mib.dbg_level >= kDebugLevel_Event)
      printf("[%s] Success to create processing tx message thread\n", STR_OK);
  }

  char c = getchar();
  // Close the serial port
  close(fd);
  fclose(fp);
  return 0;
  
  // 스레드 종료 대기
  pthread_join(rx_thread, (void **)&ret);
  pthread_join(tx_thread, (void **)&ret);

  
  return 0;
}

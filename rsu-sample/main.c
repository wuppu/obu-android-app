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

#define STR_MAX_SIZE 256
#define UNAVAILABLE_LATITUDE 900000001
#define UNAVAILABLE_LONGITUDE 1800000001
#define DEFAULT_TX_INTERVAL 100
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
};

// Message format
struct RefMessage
{
  int8_t id[4];
  uint32_t type;
  uint32_t body_len;
  int32_t latitude;
  int32_t longitude;
} __attribute__((__packed__));

/* 전역 변수 */
int fd;
pthread_t tx_thread;
pthread_t rx_thread;
struct MIB g_mib;

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
  printf(" --lat <ref_latitude>             Reference latitude. If not specified, set to 900000001\n");
  printf(" --lng <ref_longitude>            Referfence longitude. if not specified, set to 1800000001\n");
  printf(" --interval <tx_interval (mesc)>  Transmit interval(tx_interval >= 100 msec). If not specified, set to 100 msec\n");
  //printf(" --dbg <dbg_level>                Print log level. If not specified, set to 1\n");
  //printf("     0: None, 1: Error, 2: Event, 3: Info, 4: Debug\n");
  printf("\n\n");
}

/**
 * @brief 입력 파라미터 처리
 * @param [in] argc 파라미터 개수
 * @param [in] argv 파라미터 내용물
 * */
int ProcessingInputParameter(int argc, char *argv[]) {
  g_mib.device_name[0] = '\0';
  g_mib.ref_latitude = UNAVAILABLE_LATITUDE;
  g_mib.ref_longitude = UNAVAILABLE_LONGITUDE;
  g_mib.interval = DEFAULT_TX_INTERVAL;

  for (int i = 0; i < argc; i++) {
    
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
  printf("[%s] input parameter: \n", STR_DEBUG);
  printf("  device_name: %s\n", g_mib.device_name);
  printf("  ref_latitude: %d\n", g_mib.ref_latitude);
  printf("  ref_longitude: %d\n", g_mib.ref_longitude);
  printf("  interval: %d\n", g_mib.interval);

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
 * @brief 메시지 송신 스레스 프로세스
 * @param [in] data unused
 * */
void *ProcessingTxMessage(void *data) {
  (void *)data;

  while (true) {
    // Create send message
    struct RefMessage temp;
    temp.id[0] = 'H';
    temp.id[1] = 'Y';
    temp.id[2] = 'E';
    temp.id[3] = 'S';
    temp.type = 1;
    temp.body_len = 8;
    temp.latitude = g_mib.ref_latitude;
    temp.longitude = g_mib.ref_longitude;

    // transmit send message
    int send_len = write(fd, &temp, sizeof(struct RefMessage));
    if (send_len < 0) {
      printf("[%s] Fail to send the message - ret: %d\n", STR_FAIL, send_len);
      continue;
    }
    else {
      printf("[%s] Success to send the message - send_len: %d\n", STR_SEND, send_len);
    }

    // Print send message dump
    unsigned char *ptr = (unsigned char *)&temp;
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

    usleep(g_mib.interval * 1000);
  }
  
}


/**
 * @brief 메시지 수신 스레드 프로세스
 * @param [in] data unused
 * */
void *ProcessingRxMessage(void *data) {
  (void *)data;

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
      printf("[%s] Fail to read the message - ret: %d\n", STR_FAIL, recv_len);
      continue;
    }
    else {
      printf("[%s] Success to read the message - recv_len: %d\n", STR_RECV, recv_len);
    }

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


int main(int argc, char *argv[]) {
  int ret;

  if (argc < 3) {
    Usage(argv[0]);
    exit(0);
  }

  ret = ProcessingInputParameter(argc, argv);
  if (ret < 0) {
    printf("[%s] Fail to process input parameters\n", STR_FAIL);
    exit(0);
  }
  else {
    printf("[%s] Success to process input parameters\n", STR_OK);
  }

  // 시리얼 포트 초기화
  ret = SerialPortInit(g_mib.device_name);
  if (ret < 0) {
    printf("[%s] Fail to initialize the serial port - device_name: %s\n", STR_FAIL, g_mib.device_name);
    exit(0);
  }
  else {
    printf("[%s] Success to initialize the serial port - device_name: %s\n", STR_OK, g_mib.device_name);
  }

  // 수신 스레드 시작
  ret = pthread_create(&rx_thread, NULL, ProcessingRxMessage, NULL);
  if (ret < 0) {
    printf("[%s] Fail to create processing rx message thread\n", STR_FAIL);
    exit(0);
  }
  else {
    printf("[%s] Success to create processing rx message thread\n", STR_OK);
  }

  // 송신 스레드 시작
  ret = pthread_create(&tx_thread, NULL, ProcessingTxMessage, NULL);
  if (ret < 0) {
    printf("[%s] Fail to create processing tx message thread\n", STR_FAIL);
    exit(0);
  }
  else {
    printf("[%s] Success to create processing tx message thread\n", STR_OK);
  }

  // 스레드 종료 대기
  pthread_join(rx_thread, (void **)&ret);
  pthread_join(tx_thread, (void **)&ret);

  // Close the serial port
  close(fd);
  return 0;
}

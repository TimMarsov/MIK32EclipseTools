/*
 * main.c
 *
 *  Created on: 14 июн. 2026 г.
 *      Author: Tim Marsov
 *
 *      Данный шаблон является просто Blink'ом,
 *      библиотеки и пины заданы в заголовочном файле main.h
 *
 *      Информация по плагину/связи со мной и поддержке в about
 *
 */

#include "main.h"

void SystemClock_Config();
void GPIO_Init();

int main()
{
    SystemClock_Config();

    GPIO_Init();

    while (1)
    {
        HAL_GPIO_TogglePin(LED1_PORT, LED1_PIN);
        HAL_GPIO_TogglePin(LED2_PORT, LED2_PIN);

        HAL_DelayMs(500);
    }
}

void SystemClock_Config(void)
{
    PCC_InitTypeDef PCC_OscInit = {0};

    PCC_OscInit.OscillatorEnable = PCC_OSCILLATORTYPE_ALL;
    PCC_OscInit.FreqMon.OscillatorSystem = PCC_OSCILLATORTYPE_OSC32M;
    PCC_OscInit.FreqMon.ForceOscSys = PCC_FORCE_OSC_SYS_UNFIXED;
    PCC_OscInit.FreqMon.Force32KClk = PCC_FREQ_MONITOR_SOURCE_OSC32K;
    PCC_OscInit.AHBDivider = 0;
    PCC_OscInit.APBMDivider = 0;
    PCC_OscInit.APBPDivider = 0;
    PCC_OscInit.HSI32MCalibrationValue = 128;
    PCC_OscInit.LSI32KCalibrationValue = 8;
    PCC_OscInit.RTCClockSelection = PCC_RTC_CLOCK_SOURCE_AUTO;
    PCC_OscInit.RTCClockCPUSelection = PCC_CPU_RTC_CLOCK_SOURCE_OSC32K;
    HAL_PCC_Config(&PCC_OscInit);
}

void GPIO_Init()
{
    GPIO_InitTypeDef GPIO_InitStruct = {0};

    __HAL_PCC_GPIO_0_CLK_ENABLE();
    __HAL_PCC_GPIO_1_CLK_ENABLE();

    GPIO_InitStruct.Mode = HAL_GPIO_MODE_GPIO_OUTPUT;
    GPIO_InitStruct.Pull = HAL_GPIO_PULL_NONE;

    GPIO_InitStruct.Pin = LED1_PIN;
    HAL_GPIO_Init(LED1_PORT, &GPIO_InitStruct);
    
    GPIO_InitStruct.Pin = LED2_PIN;
    HAL_GPIO_Init(LED2_PORT, &GPIO_InitStruct);
}

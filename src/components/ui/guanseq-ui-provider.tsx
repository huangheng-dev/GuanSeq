"use client";

import { App, ConfigProvider, type ThemeConfig } from "antd";
import zhCN from "antd/locale/zh_CN";
import type { ReactNode } from "react";

const guanseqTheme: ThemeConfig = {
  cssVar: { prefix: "gs" },
  token: {
    colorPrimary: "#112c49",
    colorInfo: "#2c638e",
    colorSuccess: "#24734d",
    colorWarning: "#a75a0a",
    colorError: "#c8392d",
    colorText: "#17202c",
    colorTextSecondary: "#586575",
    colorTextTertiary: "#657483",
    colorBorder: "#d7dde3",
    colorBorderSecondary: "#e7ebee",
    colorBgLayout: "#e9edf1",
    colorBgContainer: "#ffffff",
    colorFillAlter: "#f5f7f8",
    borderRadius: 10,
    borderRadiusLG: 16,
    borderRadiusSM: 7,
    controlHeight: 36,
    controlHeightLG: 40,
    controlHeightSM: 32,
    controlItemBgActive: "#e8eff5",
    controlItemBgActiveHover: "#dce8f0",
    fontFamily: '"Noto Sans SC Variable", "Microsoft YaHei", sans-serif',
    fontSize: 13,
    boxShadowSecondary: "0 16px 38px rgb(13 35 56 / 17%)",
    motionDurationFast: "0.14s",
    motionDurationMid: "0.2s",
  },
  components: {
    Button: {
      borderRadius: 10,
      fontWeight: 650,
      iconGap: 7,
      defaultBg: "#ffffff",
      defaultBorderColor: "#b8c1ca",
      defaultHoverBg: "#f7f9fa",
      defaultHoverBorderColor: "#aeb8c2",
      defaultShadow: "none",
      primaryShadow: "none",
      dangerShadow: "none",
    },
    Input: {
      activeBg: "#ffffff",
      activeBorderColor: "#789bb6",
      activeShadow: "0 0 0 3px rgb(44 99 142 / 16%)",
      hoverBg: "#ffffff",
      hoverBorderColor: "#b8c5cf",
    },
    Select: {
      activeBorderColor: "#789bb6",
      activeOutlineColor: "rgb(44 99 142 / 16%)",
      hoverBorderColor: "#b8c5cf",
      optionActiveBg: "#f0f4f7",
      optionSelectedBg: "#e8eff5",
      optionSelectedColor: "#112c49",
      optionSelectedFontWeight: 650,
      optionHeight: 34,
      optionPadding: "7px 10px",
      selectorBg: "#fbfcfc",
      zIndexPopup: 120,
    },
    Modal: {
      titleFontSize: 18,
      titleLineHeight: 1.35,
      contentBg: "#ffffff",
      headerBg: "#ffffff",
      footerBg: "#f8fafb",
    },
    Drawer: { colorBgElevated: "#ffffff" },
    Table: {
      borderColor: "#d7dde3",
      headerBg: "#f5f7f8",
      headerColor: "#586575",
      rowHoverBg: "#f4f7f9",
      rowSelectedBg: "#edf3f7",
      rowSelectedHoverBg: "#e7eff5",
    },
  },
};

export function GuanSeqUIProvider({ children }: { children: ReactNode }) {
  return (
    <ConfigProvider locale={zhCN} componentSize="middle" button={{ autoInsertSpace: false }} theme={guanseqTheme}>
      <App className="guanseqAntApp">{children}</App>
    </ConfigProvider>
  );
}

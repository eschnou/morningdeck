import path from "path";
import { Config } from "@remotion/cli/config";
import { webpackOverride } from "./webpack-override.mjs";

Config.setVideoImageFormat("jpeg");
Config.setOverwriteOutput(true);
Config.setPublicDir(path.resolve(process.cwd(), "public"));
Config.overrideWebpackConfig(webpackOverride);

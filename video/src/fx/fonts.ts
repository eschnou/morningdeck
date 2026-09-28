import { loadFont as loadInter } from "@remotion/google-fonts/Inter";
import { loadFont as loadSerif } from "@remotion/google-fonts/InstrumentSerif";

export const inter = loadInter("normal", { weights: ["400", "500", "600", "700", "800"], subsets: ["latin"] }).fontFamily;
export const serif = loadSerif("italic", { weights: ["400"], subsets: ["latin"] }).fontFamily;

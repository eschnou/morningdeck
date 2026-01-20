import { motion, useInView } from "framer-motion";
import { useRef, useState } from "react";
import { Monitor, Mail } from "lucide-react";

const ProductPreview = () => {
  const ref = useRef(null);
  const isInView = useInView(ref, { once: true, margin: "-100px" });
  const [activeTab, setActiveTab] = useState<"app" | "email">("app");

  return (
    <section ref={ref} id="how-it-works" className="py-24 bg-surface relative overflow-hidden">
      <div className="container mx-auto px-6">
        {/* Section header */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={isInView ? { opacity: 1, y: 0 } : {}}
          transition={{ duration: 0.6 }}
          className="text-center mb-12"
        >
          <span className="section-header">The Experience</span>
          <h2 className="font-body text-4xl md:text-5xl font-bold text-headline mt-4 mb-6 tracking-tight">
            Your morning briefing,<br />
            <span className="gradient-text">reimagined</span>
          </h2>
        </motion.div>

        {/* Tab switcher */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={isInView ? { opacity: 1, y: 0 } : {}}
          transition={{ duration: 0.6, delay: 0.1 }}
          className="flex justify-center gap-2 mb-10"
        >
          <button
            onClick={() => setActiveTab("app")}
            className={`flex items-center gap-2 px-5 py-2.5 rounded-lg text-sm font-medium transition-all ${
              activeTab === "app"
                ? "btn-primary shadow-lg"
                : "bg-white border border-border text-ink hover:bg-secondary"
            }`}
          >
            <Monitor className="w-4 h-4" />
            Web App
          </button>
          <button
            onClick={() => setActiveTab("email")}
            className={`flex items-center gap-2 px-5 py-2.5 rounded-lg text-sm font-medium transition-all ${
              activeTab === "email"
                ? "btn-primary shadow-lg"
                : "bg-white border border-border text-ink hover:bg-secondary"
            }`}
          >
            <Mail className="w-4 h-4" />
            Daily Email
          </button>
        </motion.div>

        {/* Screenshots */}
        <motion.div
          initial={{ opacity: 0, y: 40 }}
          animate={isInView ? { opacity: 1, y: 0 } : {}}
          transition={{ duration: 0.8, delay: 0.2 }}
          className="max-w-5xl mx-auto"
        >
          <div className="relative">
            {/* App screenshot */}
            <motion.div
              initial={false}
              animate={{
                opacity: activeTab === "app" ? 1 : 0,
                scale: activeTab === "app" ? 1 : 0.95,
                x: activeTab === "app" ? 0 : -20,
              }}
              transition={{ duration: 0.4 }}
              className={`${activeTab === "app" ? "block" : "hidden"}`}
            >
              <div className="bg-white rounded-2xl overflow-hidden shadow-2xl shadow-black/10 border border-border">
                {/* Browser chrome */}
                <div className="bg-secondary/50 border-b border-border px-4 py-3 flex items-center gap-2">
                  <div className="flex gap-1.5">
                    <div className="w-3 h-3 rounded-full bg-red-400" />
                    <div className="w-3 h-3 rounded-full bg-yellow-400" />
                    <div className="w-3 h-3 rounded-full bg-green-400" />
                  </div>
                  <div className="flex-1 mx-4">
                    <div className="bg-white rounded-lg px-3 py-1.5 text-xs text-muted-foreground text-center border border-border">
                      app.morningdeck.com/briefs/ai-daily-news
                    </div>
                  </div>
                </div>
                <img
                  src="/app-screenshot.png"
                  alt="Morning Deck web application showing AI Daily News feed with scored articles"
                  className="w-full"
                />
              </div>
              <p className="text-center mt-6 text-muted-foreground text-sm">
                Browse, filter, and save articles with AI-powered relevance scoring
              </p>
            </motion.div>

            {/* Email screenshot */}
            <motion.div
              initial={false}
              animate={{
                opacity: activeTab === "email" ? 1 : 0,
                scale: activeTab === "email" ? 1 : 0.95,
                x: activeTab === "email" ? 0 : 20,
              }}
              transition={{ duration: 0.4 }}
              className={`${activeTab === "email" ? "block" : "hidden"}`}
            >
              <div className="bg-white rounded-2xl overflow-hidden shadow-2xl shadow-black/10 border border-border max-w-2xl mx-auto">
                {/* Email chrome */}
                <div className="bg-secondary/50 border-b border-border px-4 py-3 flex items-center gap-3">
                  <div className="w-10 h-10 rounded-full bg-accent/10 flex items-center justify-center">
                    <Mail className="w-5 h-5 text-accent" />
                  </div>
                  <div className="flex-1">
                    <div className="text-sm font-semibold text-headline">Morning Deck</div>
                    <div className="text-xs text-muted-foreground">briefs@morningdeck.com</div>
                  </div>
                  <div className="text-xs text-muted-foreground">7:00 AM</div>
                </div>
                <img
                  src="/email-screenshot.png"
                  alt="Morning Deck daily email briefing with top stories and summaries"
                  className="w-full"
                />
              </div>
              <p className="text-center mt-6 text-muted-foreground text-sm">
                AI-generated daily digest with executive summary and top stories
              </p>
            </motion.div>
          </div>
        </motion.div>
      </div>
    </section>
  );
};

export default ProductPreview;

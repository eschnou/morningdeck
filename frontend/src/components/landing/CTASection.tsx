import { motion, useInView } from "framer-motion";
import { useRef, useState, useEffect } from "react";
import { ArrowRight, CheckCircle, Loader2 } from "lucide-react";
import { apiClient } from "@/lib/api";
import { toast } from "@/hooks/use-toast";

const CTASection = () => {
  const ref = useRef(null);
  const isInView = useInView(ref, { once: true, margin: "-100px" });
  const [email, setEmail] = useState("");
  const [submitted, setSubmitted] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [waitlistCount, setWaitlistCount] = useState(0);

  // Fetch waitlist count on mount
  useEffect(() => {
    const fetchStats = async () => {
      try {
        const stats = await apiClient.getWaitlistStats();
        setWaitlistCount(stats.count);
      } catch {
        // Silently fail - will show seed count only
      }
    };
    fetchStats();
  }, []);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!email || isLoading) return;

    setIsLoading(true);
    try {
      await apiClient.joinWaitlist(email);
      setSubmitted(true);
      setWaitlistCount((prev) => prev + 1);
      setEmail("");
      toast({
        title: "You're on the list!",
        description: "We'll be in touch soon with early access.",
      });
    } catch (error: unknown) {
      const message = error instanceof Error ? error.message : "Please try again";
      toast({
        title: "Failed to join waitlist",
        description: message,
        variant: "destructive",
      });
    } finally {
      setIsLoading(false);
    }
  };

  const displayCount = waitlistCount;

  return (
    <section ref={ref} id="cta" className="py-24 bg-ink relative overflow-hidden">
      {/* Gradient decorations */}
      <div className="absolute top-0 left-0 w-full h-px bg-gradient-to-r from-transparent via-accent/50 to-transparent" />
      <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_top,hsl(250,100%,65%,0.15),transparent_50%)]" />
      <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_bottom_right,hsl(280,100%,70%,0.1),transparent_50%)]" />

      <div className="container mx-auto px-6 relative z-10">
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={isInView ? { opacity: 1, y: 0 } : {}}
          transition={{ duration: 0.6 }}
          className="max-w-3xl mx-auto text-center"
        >
          <span className="inline-flex items-center gap-2 px-3 py-1 rounded-full text-xs font-semibold uppercase tracking-wider bg-accent/20 text-accent-glow mb-6">
            Early Access
          </span>

          <h2 className="font-body text-4xl md:text-5xl lg:text-6xl font-bold text-white mt-4 mb-6 tracking-tight">
            Start every morning<br />
            <span className="bg-gradient-to-r from-accent to-purple-400 bg-clip-text text-transparent">ahead of the curve</span>
          </h2>

          <p className="text-white/70 text-lg mb-10 max-w-xl mx-auto">
            Join the waitlist for early access. Be among the first to experience
            personalized AI news intelligence.
          </p>

          {!submitted ? (
            <motion.form
              onSubmit={handleSubmit}
              initial={{ opacity: 0, y: 20 }}
              animate={isInView ? { opacity: 1, y: 0 } : {}}
              transition={{ duration: 0.6, delay: 0.2 }}
              className="flex flex-col sm:flex-row gap-3 max-w-md mx-auto"
            >
              <input
                type="email"
                placeholder="Enter your email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                disabled={isLoading}
                className="flex-1 px-4 py-3 bg-white/10 border border-white/20 rounded-lg text-white placeholder:text-white/50 focus:outline-none focus:border-accent focus:ring-1 focus:ring-accent transition-all disabled:opacity-50"
                required
              />
              <button
                type="submit"
                disabled={isLoading}
                className="bg-gradient-to-r from-accent to-purple-500 text-white font-medium px-6 py-3 rounded-lg hover:shadow-lg hover:shadow-accent/30 transition-all flex items-center justify-center gap-2 group disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {isLoading ? (
                  <>
                    <Loader2 className="w-4 h-4 animate-spin" />
                    Joining...
                  </>
                ) : (
                  <>
                    Join waitlist
                    <ArrowRight className="w-4 h-4 group-hover:translate-x-1 transition-transform" />
                  </>
                )}
              </button>
            </motion.form>
          ) : (
            <motion.div
              initial={{ opacity: 0, scale: 0.95 }}
              animate={{ opacity: 1, scale: 1 }}
              className="flex items-center justify-center gap-2 text-accent"
            >
              <CheckCircle className="w-5 h-5" />
              <span className="font-medium">You're on the list! We'll be in touch soon.</span>
            </motion.div>
          )}

          {/* Stats */}
          <motion.div
            initial={{ opacity: 0 }}
            animate={isInView ? { opacity: 1 } : {}}
            transition={{ duration: 0.6, delay: 0.4 }}
            className="flex items-center justify-center gap-8 mt-12 pt-8 border-t border-white/10"
          >
            <div className="text-center">
              <div className="font-body text-3xl font-bold text-white">Q2 2026</div>
              <div className="text-xs text-white/50 uppercase tracking-wider mt-1">Launch date</div>
            </div>
            <div className="w-px h-12 bg-white/10" />
            <div className="text-center">
              <div className="font-body text-3xl font-bold text-white">Free</div>
              <div className="text-xs text-white/50 uppercase tracking-wider mt-1">Beta access</div>
            </div>
          </motion.div>
        </motion.div>
      </div>
    </section>
  );
};

export default CTASection;

import { motion } from "framer-motion";
import { Link } from "react-router-dom";

const Header = () => {
  return (
    <motion.header
      initial={{ opacity: 0, y: -10 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.5 }}
      className="fixed top-0 left-0 right-0 z-50 bg-white/80 backdrop-blur-xl border-b border-border/50"
    >
      <div className="container mx-auto px-6 py-4">
        <div className="flex items-center justify-between">
          {/* Logo & Brand */}
          <div className="flex items-center gap-3">
            <img src="/logo.png" alt="Morning Deck" className="w-8 h-8" />
            <span className="font-body text-xl font-semibold text-headline">Morning Deck</span>
          </div>

          {/* Navigation */}
          <nav className="hidden md:flex items-center gap-8">
            <a href="#features" className="nav-link">
              Features
            </a>
            <a href="#how-it-works" className="nav-link">
              How it works
            </a>
            <a href="#pricing" className="nav-link">
              Pricing
            </a>
          </nav>

          {/* CTA */}
          <div className="flex items-center gap-4">
            <Link to="/login/" className="hidden sm:block text-sm font-medium text-ink-light hover:text-ink transition-colors">
              Sign in
            </Link>
            <a href="#cta" className="btn-primary text-sm px-4 py-2">
              Join the waitlist
            </a>
          </div>
        </div>
      </div>
    </motion.header>
  );
};

export default Header;

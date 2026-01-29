import { Toaster } from "@/components/ui/toaster";
import { Toaster as Sonner } from "@/components/ui/sonner";
import { TooltipProvider } from "@/components/ui/tooltip";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import { AuthProvider } from "@/contexts/AuthContext";
import { ConfigProvider, useConfig } from "@/contexts/ConfigContext";
import { ProtectedRoute } from "@/components/ProtectedRoute";
import { AppLayout } from "@/components/layout/AppLayout";
import { AdminLayout } from "@/components/layout/AdminLayout";
import LandingPage from "./pages/LandingPage";
import Login from "./pages/Login";
import Register from "./pages/Register";
import VerifyEmailPage from "./pages/VerifyEmailPage";
import VerificationPendingPage from "./pages/VerificationPendingPage";
import SourcesPage from "./pages/SourcesPage";
import SourceDetailPage from "./pages/SourceDetailPage";
import BriefsRedirect from "./pages/BriefsRedirect";
import BriefDetailPage from "./pages/BriefDetailPage";
import BriefReportsPage from "./pages/BriefReportsPage";
import ReportDetailPage from "./pages/ReportDetailPage";
import Settings from "./pages/Settings";
import Admin from "./pages/Admin";
import NotFound from "./pages/NotFound";

const queryClient = new QueryClient();

// Component to handle conditional landing page based on config
const LandingRoute = () => {
  const { config, isLoading } = useConfig();

  if (isLoading) {
    return null; // Or a loading spinner
  }

  if (config?.selfHostedMode) {
    return <Login />;
  }

  return <LandingPage />;
};

const App = () => (
  <QueryClientProvider client={queryClient}>
    <TooltipProvider>
      <ConfigProvider>
        <AuthProvider>
          <Toaster />
          <Sonner />
          <BrowserRouter>
            <Routes>
              {/* Public routes */}
              <Route path="/" element={<LandingRoute />} />
            <Route path="/login/" element={<Login />} />
            <Route path="/login" element={<Navigate to="/login/" replace />} />
            <Route path="/auth/login" element={<Navigate to="/login/" replace />} />
            <Route path="/auth/register" element={<Register />} />
            <Route path="/verify-email" element={<VerifyEmailPage />} />
            <Route path="/auth/verify-email" element={<VerifyEmailPage />} />
            <Route path="/auth/verification-pending" element={<VerificationPendingPage />} />

            {/* Protected routes with sidebar layout */}
            <Route
              element={
                <ProtectedRoute>
                  <AppLayout />
                </ProtectedRoute>
              }
            >
              <Route path="/sources" element={<SourcesPage />} />
              <Route path="/sources/:id" element={<SourceDetailPage />} />
              <Route path="/briefs" element={<BriefsRedirect />} />
              <Route path="/briefs/:id" element={<BriefDetailPage />} />
              <Route path="/briefs/:id/reports" element={<BriefReportsPage />} />
              <Route path="/briefs/:id/reports/:reportId" element={<ReportDetailPage />} />
              <Route path="/settings" element={<Settings />} />
            </Route>

            {/* Admin route with admin sidebar layout */}
            <Route
              element={
                <ProtectedRoute requireAdmin>
                  <AdminLayout />
                </ProtectedRoute>
              }
            >
              <Route path="/admin" element={<Admin />} />
            </Route>

            {/* Redirect legacy routes to briefs */}
            <Route path="/dashboard" element={<Navigate to="/briefs" replace />} />
            <Route path="/home" element={<Navigate to="/briefs" replace />} />

            {/* Catch-all */}
            <Route path="*" element={<NotFound />} />
            </Routes>
          </BrowserRouter>
        </AuthProvider>
      </ConfigProvider>
    </TooltipProvider>
  </QueryClientProvider>
);

export default App;

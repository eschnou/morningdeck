import { useState } from 'react';
import { useNavigate, useLocation, Link } from 'react-router-dom';
import { apiClient } from '@/lib/api';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { toast } from '@/hooks/use-toast';
import { Mail, Loader2, ArrowRight } from 'lucide-react';

interface LocationState {
  email?: string;
}

export default function VerificationPendingPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const state = location.state as LocationState | null;

  const [isResending, setIsResending] = useState(false);
  const email = state?.email;

  const handleResend = async () => {
    if (!email) {
      return;
    }

    setIsResending(true);
    try {
      await apiClient.resendVerificationEmail(email);
      toast({
        title: 'Verification email sent',
        description: 'Please check your inbox for the verification link.',
      });
    } catch (error: unknown) {
      toast({
        title: 'Failed to resend',
        description:
          error instanceof Error
            ? error.message
            : 'Please try again later.',
        variant: 'destructive',
      });
    } finally {
      setIsResending(false);
    }
  };

  return (
    <div className="flex min-h-screen flex-col items-center justify-center bg-muted/30 p-4">
      <div className="mb-8 text-center">
        <img
          src="/logo.png"
          alt="Morning Deck"
          className="mx-auto h-16 w-16 cursor-pointer"
          onClick={() => navigate('/')}
        />
        <h1
          className="mt-4 text-3xl font-bold tracking-tight cursor-pointer"
          onClick={() => navigate('/')}
        >
          Morning Deck
        </h1>
      </div>

      <Card className="w-full max-w-md shadow-lg">
        <CardContent className="pt-6">
          <div className="flex flex-col items-center py-4">
            <div className="rounded-full bg-primary/10 p-4">
              <Mail className="h-8 w-8 text-primary" />
            </div>
            <h2 className="mt-4 text-xl font-semibold">Check your email</h2>
            <p className="mt-2 text-center text-muted-foreground">
              We've sent a verification link to{' '}
              {email ? (
                <span className="font-medium text-foreground">{email}</span>
              ) : (
                'your email address'
              )}
              . Click the link to verify your account.
            </p>

            <div className="mt-6 w-full space-y-4">
              <div className="rounded-lg bg-muted/50 p-4 text-sm text-muted-foreground">
                <p className="font-medium text-foreground">Didn't receive the email?</p>
                <ul className="mt-2 list-disc list-inside space-y-1">
                  <li>Check your spam or junk folder</li>
                  <li>Make sure you entered the correct email</li>
                  <li>Wait a few minutes and try again</li>
                </ul>
              </div>

              <Button
                variant="outline"
                className="w-full"
                onClick={handleResend}
                disabled={isResending || !email}
              >
                {isResending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                Resend verification email
              </Button>

              <div className="pt-4 border-t">
                <Button className="w-full" onClick={() => navigate('/auth/login')}>
                  Continue to Sign in
                  <ArrowRight className="ml-2 h-4 w-4" />
                </Button>
              </div>
            </div>
          </div>
        </CardContent>
      </Card>

      <p className="mt-6 text-sm text-muted-foreground">
        Wrong email?{' '}
        <Link to="/auth/register" className="text-primary hover:underline font-medium">
          Register again
        </Link>
      </p>
    </div>
  );
}

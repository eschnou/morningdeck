import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams, Link } from 'react-router-dom';
import { apiClient } from '@/lib/api';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Loader2, CheckCircle2, XCircle, ArrowRight } from 'lucide-react';

type VerificationState = 'loading' | 'success' | 'error';

export default function VerifyEmailPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [state, setState] = useState<VerificationState>('loading');
  const [errorMessage, setErrorMessage] = useState<string>('');

  const token = searchParams.get('token');

  useEffect(() => {
    const verifyEmail = async () => {
      if (!token) {
        setState('error');
        setErrorMessage('No verification token provided.');
        return;
      }

      try {
        await apiClient.verifyEmailWithToken(token);
        setState('success');
      } catch (error: unknown) {
        setState('error');
        setErrorMessage(
          error instanceof Error
            ? error.message
            : 'Verification failed. The link may be invalid or expired.'
        );
      }
    };

    verifyEmail();
  }, [token]);

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
          {state === 'loading' && (
            <div className="flex flex-col items-center py-8">
              <Loader2 className="h-12 w-12 animate-spin text-primary" />
              <p className="mt-4 text-muted-foreground">Verifying your email...</p>
            </div>
          )}

          {state === 'success' && (
            <div className="flex flex-col items-center py-8">
              <CheckCircle2 className="h-12 w-12 text-green-500" />
              <h2 className="mt-4 text-xl font-semibold">Email Verified!</h2>
              <p className="mt-2 text-center text-muted-foreground">
                Your email has been successfully verified. You can now sign in to your account.
              </p>
              <Button className="mt-6" onClick={() => navigate('/auth/login')}>
                Sign in
                <ArrowRight className="ml-2 h-4 w-4" />
              </Button>
            </div>
          )}

          {state === 'error' && (
            <div className="flex flex-col items-center py-8">
              <XCircle className="h-12 w-12 text-destructive" />
              <h2 className="mt-4 text-xl font-semibold">Verification Failed</h2>
              <p className="mt-2 text-center text-muted-foreground">{errorMessage}</p>
              <div className="mt-6 flex flex-col gap-3 w-full">
                <Button variant="outline" onClick={() => navigate('/auth/login')}>
                  Go to Sign in
                </Button>
                <p className="text-center text-sm text-muted-foreground">
                  Need a new verification link?{' '}
                  <Link
                    to="/auth/login"
                    className="text-primary hover:underline font-medium"
                  >
                    Sign in to resend
                  </Link>
                </p>
              </div>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}

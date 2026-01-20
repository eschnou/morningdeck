import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Loader2, FileText } from 'lucide-react';
import { apiClient } from '@/lib/api';
import { toast } from '@/hooks/use-toast';
import { PageHeader } from '@/components/layout/PageHeader';
import { EmptyState } from '@/components/shared/EmptyState';
import { CreateBriefDialog } from '@/components/briefs/CreateBriefDialog';
import type { DayBriefDTO } from '@/types';

export default function BriefsRedirect() {
  const navigate = useNavigate();
  const [isLoading, setIsLoading] = useState(true);
  const [hasNoBriefs, setHasNoBriefs] = useState(false);
  const [showCreateDialog, setShowCreateDialog] = useState(false);

  useEffect(() => {
    const redirectToFirstBrief = async () => {
      try {
        const response = await apiClient.getDayBriefs(0, 1);
        if (response.content.length > 0) {
          navigate(`/briefs/${response.content[0].id}`, { replace: true });
        } else {
          setHasNoBriefs(true);
          setIsLoading(false);
        }
      } catch (error: unknown) {
        toast({
          title: 'Failed to load briefs',
          description: error instanceof Error ? error.message : 'An error occurred',
          variant: 'destructive',
        });
        setIsLoading(false);
      }
    };

    redirectToFirstBrief();
  }, [navigate]);

  const handleCreateSuccess = (brief: DayBriefDTO) => {
    navigate(`/briefs/${brief.id}`, { replace: true });
  };

  if (isLoading) {
    return (
      <div className="flex min-h-[50vh] items-center justify-center">
        <Loader2 className="h-8 w-8 animate-spin text-primary" />
      </div>
    );
  }

  if (hasNoBriefs) {
    return (
      <>
        <PageHeader title="Welcome to Morning Deck" />
        <main className="flex-1 p-4">
          <EmptyState
            icon={FileText}
            title="Create your first brief"
            description="Get started by creating a brief to receive personalized news summaries."
            action={{
              label: 'Create Brief',
              onClick: () => setShowCreateDialog(true),
            }}
          />
        </main>

        <CreateBriefDialog
          open={showCreateDialog}
          onOpenChange={setShowCreateDialog}
          onSuccess={handleCreateSuccess}
        />
      </>
    );
  }

  return null;
}

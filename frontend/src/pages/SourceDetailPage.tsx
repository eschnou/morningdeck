import { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { PageHeader, BreadcrumbItem } from '@/components/layout/PageHeader';
import { SourceForm } from '@/components/sources/SourceForm';
import { SourceItemsList } from '@/components/sources/SourceItemsList';
import { StatusBadge } from '@/components/shared/StatusBadge';
import { ConfirmDialog } from '@/components/shared/ConfirmDialog';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip';
import { ArrowLeft, Pencil, Trash2, ExternalLink, AlertCircle, RefreshCw, Mail, Copy } from 'lucide-react';
import { formatDistanceToNow, format } from 'date-fns';
import { parseApiDate } from '@/lib/utils';
import { apiClient } from '@/lib/api';
import { toast } from '@/hooks/use-toast';
import type { SourceDTO, UpdateSourceRequest } from '@/types';

export default function SourceDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [source, setSource] = useState<SourceDTO | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isEditing, setIsEditing] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const [isDeleting, setIsDeleting] = useState(false);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [showDeleteDialog, setShowDeleteDialog] = useState(false);
  const [briefSourceIds, setBriefSourceIds] = useState<string[]>([]);

  const fetchSource = useCallback(async () => {
    setIsLoading(true);
    try {
      const data = await apiClient.getSource(id!);
      setSource(data);
    } catch (error: unknown) {
      toast({
        title: 'Failed to load source',
        description: error instanceof Error ? error.message : 'An error occurred',
        variant: 'destructive',
      });
      navigate('/sources');
    } finally {
      setIsLoading(false);
    }
  }, [id, navigate]);

  useEffect(() => {
    if (id) {
      fetchSource();
    }
  }, [id, fetchSource]);

  // Fetch source IDs for h/l navigation within the same brief
  useEffect(() => {
    if (!source?.briefingId) return;
    const fetchBriefSources = async () => {
      try {
        const response = await apiClient.getBriefingSources(source.briefingId, 0, 100);
        setBriefSourceIds(response.content.map((s) => s.id));
      } catch {
        // Silently fail - navigation just won't work
      }
    };
    fetchBriefSources();
  }, [source?.briefingId]);

  const handlePrevSource = useCallback(() => {
    if (!id || briefSourceIds.length === 0) return;
    const currentIndex = briefSourceIds.indexOf(id);
    if (currentIndex > 0) {
      navigate(`/sources/${briefSourceIds[currentIndex - 1]}`);
    }
  }, [id, briefSourceIds, navigate]);

  const handleNextSource = useCallback(() => {
    if (!id || briefSourceIds.length === 0) return;
    const currentIndex = briefSourceIds.indexOf(id);
    if (currentIndex >= 0 && currentIndex < briefSourceIds.length - 1) {
      navigate(`/sources/${briefSourceIds[currentIndex + 1]}`);
    }
  }, [id, briefSourceIds, navigate]);

  const handleUpdate = async (data: UpdateSourceRequest) => {
    setIsSaving(true);
    try {
      const updated = await apiClient.updateSource(id!, data);
      setSource(updated);
      setIsEditing(false);
      toast({
        title: 'Source updated',
        description: 'Your changes have been saved.',
      });
    } catch (error: unknown) {
      toast({
        title: 'Failed to update source',
        description: error instanceof Error ? error.message : 'An error occurred',
        variant: 'destructive',
      });
    } finally {
      setIsSaving(false);
    }
  };

  const handleDelete = async () => {
    setIsDeleting(true);
    try {
      const briefingId = source?.briefingId;
      await apiClient.deleteSource(id!);
      toast({
        title: 'Source deleted',
        description: 'The source has been removed.',
      });
      navigate(briefingId ? `/briefs/${briefingId}` : '/briefs');
    } catch (error: unknown) {
      toast({
        title: 'Failed to delete source',
        description: error instanceof Error ? error.message : 'An error occurred',
        variant: 'destructive',
      });
    } finally {
      setIsDeleting(false);
      setShowDeleteDialog(false);
    }
  };

  const handleRefresh = async () => {
    setIsRefreshing(true);
    try {
      const updated = await apiClient.refreshSource(id!);
      setSource(updated);
      toast({
        title: 'Refresh started',
        description: 'The source is being refreshed. New items will appear shortly.',
      });
    } catch (error: unknown) {
      toast({
        title: 'Failed to refresh source',
        description: error instanceof Error ? error.message : 'An error occurred',
        variant: 'destructive',
      });
    } finally {
      setIsRefreshing(false);
    }
  };


  if (isLoading) {
    return (
      <>
        <PageHeader title="Loading..." />
        <main className="flex-1 p-4">
          <Skeleton className="h-64" />
        </main>
      </>
    );
  }

  if (!source) {
    return null;
  }

  const breadcrumbs: BreadcrumbItem[] = [
    { label: 'Briefs', path: '/briefs' },
    { label: source.briefingTitle, path: `/briefs/${source.briefingId}` },
    { label: 'Sources', path: `/briefs/${source.briefingId}` },
    { label: source.name },
  ];

  return (
    <>
      <PageHeader title={source.name} breadcrumbs={breadcrumbs}>
        <Link to={`/briefs/${source.briefingId}`}>
          <Button variant="ghost" size="sm">
            <ArrowLeft className="mr-2 h-4 w-4" />
            Back to Brief
          </Button>
        </Link>
      </PageHeader>

      <main className="flex-1 p-4">
        <Card>
          <CardHeader>
            <div className="flex items-center justify-between">
              <CardTitle className="flex items-center gap-2">
                {source.name}
                <StatusBadge status={source.status} />
              </CardTitle>
              {!isEditing && (
                <div className="flex gap-2">
                  {source.type !== 'EMAIL' && (
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={handleRefresh}
                      disabled={isRefreshing || source.status !== 'ACTIVE'}
                    >
                      <RefreshCw className={`mr-2 h-4 w-4 ${isRefreshing ? 'animate-spin' : ''}`} />
                      {isRefreshing ? 'Refreshing...' : 'Refresh'}
                    </Button>
                  )}
                  <Button variant="outline" size="sm" onClick={() => setIsEditing(true)}>
                    <Pencil className="mr-2 h-4 w-4" />
                    Edit
                  </Button>
                  <Button
                    variant="outline"
                    size="sm"
                    className="text-destructive hover:text-destructive"
                    onClick={() => setShowDeleteDialog(true)}
                  >
                    <Trash2 className="mr-2 h-4 w-4" />
                    Delete
                  </Button>
                </div>
              )}
            </div>
          </CardHeader>
          <CardContent className="pt-0">
            {isEditing ? (
              <SourceForm
                source={source}
                onSubmit={handleUpdate}
                onCancel={() => setIsEditing(false)}
                isLoading={isSaving}
              />
            ) : (
              <div className="space-y-3">
                {/* Email address or URL row */}
                {source.type === 'EMAIL' && source.emailAddress ? (
                  <div className="flex items-center gap-2 p-3 bg-muted rounded-md">
                    <Mail className="h-4 w-4 text-muted-foreground flex-shrink-0" />
                    <code className="flex-1 text-sm font-mono">{source.emailAddress}</code>
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => {
                        navigator.clipboard.writeText(source.emailAddress!);
                        toast({ title: 'Copied', description: 'Email address copied to clipboard' });
                      }}
                    >
                      <Copy className="h-4 w-4" />
                    </Button>
                  </div>
                ) : source.type === 'REDDIT' ? (
                  <a
                    href={`https://reddit.com/r/${source.url.replace(/^reddit:\/\//, '')}`}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground transition-colors"
                  >
                    <span>r/{source.url.replace(/^reddit:\/\//, '')}</span>
                    <ExternalLink className="h-3 w-3 flex-shrink-0" />
                  </a>
                ) : (
                  <a
                    href={source.url}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground transition-colors"
                  >
                    <span className="truncate max-w-md">
                      {source.url.replace(/^https?:\/\//, '')}
                    </span>
                    <ExternalLink className="h-3 w-3 flex-shrink-0" />
                  </a>
                )}

                {/* Extraction prompt for WEB sources */}
                {source.type === 'WEB' && source.extractionPrompt && (
                  <div className="p-3 bg-muted/50 rounded-md">
                    <p className="text-xs font-medium text-muted-foreground mb-1">Extraction Prompt</p>
                    <p className="text-sm">{source.extractionPrompt}</p>
                  </div>
                )}

                {/* Metadata row */}
                <div className="flex flex-wrap items-center gap-x-4 gap-y-2 text-sm text-muted-foreground">
                  <Badge variant="outline" className="text-xs">
                    {source.type}
                  </Badge>

                  {source.tags && source.tags.length > 0 && (
                    <span>{source.tags.join(' • ')}</span>
                  )}

                  <span className="flex items-center gap-4">
                    <span>{source.itemCount} items</span>
                    {source.lastFetchedAt && (
                      <>
                        <span>•</span>
                        <Tooltip>
                          <TooltipTrigger asChild>
                            <span className="cursor-default">
                              Updated {formatDistanceToNow(parseApiDate(source.lastFetchedAt), { addSuffix: false })} ago
                            </span>
                          </TooltipTrigger>
                          <TooltipContent>
                            {format(parseApiDate(source.lastFetchedAt), 'PPpp')}
                          </TooltipContent>
                        </Tooltip>
                      </>
                    )}
                    <span>•</span>
                    <span>{format(new Date(source.createdAt), 'MMM yyyy')}</span>
                  </span>
                </div>

                {/* Error banner (if any) */}
                {source.lastError && (
                  <div className="flex items-center gap-2 text-sm text-destructive bg-destructive/10 rounded px-3 py-2">
                    <AlertCircle className="h-4 w-4 flex-shrink-0" />
                    <span className="truncate">{source.lastError}</span>
                  </div>
                )}
              </div>
            )}
          </CardContent>
        </Card>

        <SourceItemsList
          sourceId={id!}
          onRefresh={handleRefresh}
          onPrevSource={handlePrevSource}
          onNextSource={handleNextSource}
        />
      </main>

      <ConfirmDialog
        open={showDeleteDialog}
        onOpenChange={setShowDeleteDialog}
        title="Delete Source"
        description={`Are you sure you want to delete "${source.name}"? This action cannot be undone.`}
        confirmLabel="Delete"
        variant="destructive"
        onConfirm={handleDelete}
        isLoading={isDeleting}
      />
    </>
  );
}

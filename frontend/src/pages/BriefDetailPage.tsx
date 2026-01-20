import { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { PageHeader } from '@/components/layout/PageHeader';
import { BriefForm } from '@/components/briefs/BriefForm';
import { StatusBadge } from '@/components/shared/StatusBadge';
import { ConfirmDialog } from '@/components/shared/ConfirmDialog';
import { EmptyState } from '@/components/shared/EmptyState';
import { PaginationControls } from '@/components/shared/PaginationControls';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip';
import { Pencil, Trash2, FileText, Rss, Plus, Calendar, ListOrdered, MoreVertical, RefreshCw } from 'lucide-react';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { formatDistanceToNow, format } from 'date-fns';
import { parseApiDate } from '@/lib/utils';
import { apiClient } from '@/lib/api';
import { events, BRIEFS_CHANGED } from '@/lib/events';
import { toast } from '@/hooks/use-toast';
import { AddSourceDialog } from '@/components/sources/AddSourceDialog';
import { BriefSourceItem } from '@/components/briefs/BriefSourceItem';
import { BriefFeedList } from '@/components/briefs/BriefFeedList';
import type { DayBriefDTO, SourceDTO, DailyReportDTO, UpdateDayBriefRequest } from '@/types';

export default function BriefDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [brief, setBrief] = useState<DayBriefDTO | null>(null);
  const [sources, setSources] = useState<SourceDTO[]>([]);
  const [reports, setReports] = useState<DailyReportDTO[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isLoadingReports, setIsLoadingReports] = useState(false);
  const [isEditing, setIsEditing] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const [isExecuting, setIsExecuting] = useState(false);
  const [isDeleting, setIsDeleting] = useState(false);
  const [showDeleteDialog, setShowDeleteDialog] = useState(false);
  const [showAddSourceDialog, setShowAddSourceDialog] = useState(false);
  const [sourceToDelete, setSourceToDelete] = useState<SourceDTO | null>(null);
  const [reportsPage, setReportsPage] = useState(0);
  const [reportsTotalPages, setReportsTotalPages] = useState(0);

  const fetchData = useCallback(async () => {
    setIsLoading(true);
    try {
      const [briefData, sourcesData, reportsData] = await Promise.all([
        apiClient.getDayBrief(id!),
        apiClient.getBriefingSources(id!, 0, 100),
        apiClient.getDayBriefReports(id!, 0, 10),
      ]);
      setBrief(briefData);
      setSources(sourcesData.content);
      setReports(reportsData.content);
      setReportsTotalPages(reportsData.totalPages);
    } catch (error: unknown) {
      toast({
        title: 'Failed to load brief',
        description: error instanceof Error ? error.message : 'An error occurred',
        variant: 'destructive',
      });
      navigate('/briefs');
    } finally {
      setIsLoading(false);
    }
  }, [id, navigate]);

  useEffect(() => {
    if (id) {
      fetchData();
    }
  }, [id, fetchData]);

  const fetchReports = async (page: number) => {
    setIsLoadingReports(true);
    try {
      const reportsData = await apiClient.getDayBriefReports(id!, page, 10);
      setReports(reportsData.content);
      setReportsPage(reportsData.number);
      setReportsTotalPages(reportsData.totalPages);
    } finally {
      setIsLoadingReports(false);
    }
  };

  const handleUpdate = async (data: UpdateDayBriefRequest) => {
    setIsSaving(true);
    try {
      const updated = await apiClient.updateDayBrief(id!, data);
      setBrief(updated);
      setIsEditing(false);
      toast({
        title: 'Brief updated',
        description: 'Your changes have been saved.',
      });
    } catch (error: unknown) {
      toast({
        title: 'Failed to update brief',
        description: error instanceof Error ? error.message : 'An error occurred',
        variant: 'destructive',
      });
    } finally {
      setIsSaving(false);
    }
  };

  const handleExecute = async () => {
    setIsExecuting(true);
    try {
      await apiClient.executeDayBrief(id!);
      toast({
        title: 'Brief executed',
        description: 'A new report is being generated.',
      });
      // Refresh data
      const [updated, reportsData] = await Promise.all([
        apiClient.getDayBrief(id!),
        apiClient.getDayBriefReports(id!, 0, 10),
      ]);
      setBrief(updated);
      setReports(reportsData.content);
      setReportsPage(0);
      setReportsTotalPages(reportsData.totalPages);
    } catch (error: unknown) {
      toast({
        title: 'Failed to execute brief',
        description: error instanceof Error ? error.message : 'An error occurred',
        variant: 'destructive',
      });
    } finally {
      setIsExecuting(false);
    }
  };

  const handleDelete = async () => {
    setIsDeleting(true);
    try {
      await apiClient.deleteDayBrief(id!);
      toast({
        title: 'Brief deleted',
        description: 'The brief has been removed.',
      });
      events.emit(BRIEFS_CHANGED);
      navigate('/briefs');
    } catch (error: unknown) {
      toast({
        title: 'Failed to delete brief',
        description: error instanceof Error ? error.message : 'An error occurred',
        variant: 'destructive',
      });
    } finally {
      setIsDeleting(false);
      setShowDeleteDialog(false);
    }
  };

  const handleAddSource = async () => {
    const sourcesData = await apiClient.getBriefingSources(id!, 0, 100);
    setSources(sourcesData.content);
  };

  const handleDeleteSource = async (sourceId: string) => {
    try {
      await apiClient.deleteSource(sourceId);
      toast({
        title: 'Source removed',
        description: 'The source has been removed from this brief.',
      });
      setSources((prev) => prev.filter((s) => s.id !== sourceId));
    } catch (error: unknown) {
      toast({
        title: 'Failed to remove source',
        description: error instanceof Error ? error.message : 'An error occurred',
        variant: 'destructive',
      });
    }
    setSourceToDelete(null);
  };

  const formatScheduleTime = (time: string) => {
    const [hours, minutes] = time.split(':');
    const hour = parseInt(hours);
    const ampm = hour >= 12 ? 'PM' : 'AM';
    const displayHour = hour % 12 || 12;
    return `${displayHour}:${minutes} ${ampm}`;
  };

  if (isLoading) {
    return (
      <>
        <PageHeader title="Loading..." />
        <main className="flex-1 p-4">
          <Skeleton className="h-16 mb-4" />
          <Skeleton className="h-64" />
        </main>
      </>
    );
  }

  if (!brief) {
    return null;
  }

  if (isEditing) {
    return (
      <>
        <PageHeader title="Edit Brief" />
        <main className="flex-1 p-4 max-w-2xl">
          <Card>
            <CardContent className="p-6">
              <BriefForm
                brief={brief}
                sources={sources}
                onSubmit={handleUpdate}
                onCancel={() => setIsEditing(false)}
                isLoading={isSaving}
              />
            </CardContent>
          </Card>
        </main>
      </>
    );
  }

  return (
    <>
      <PageHeader title={brief.title} />

      <main className="flex-1 p-4 space-y-4">
        {/* Tabs with inline header */}
        <Tabs defaultValue={sources.length === 0 ? 'sources' : 'feed'} className="flex-1">
          {/* Inline Header: Title + Tabs + Menu */}
          <div className="flex flex-col sm:flex-row sm:items-center gap-3 sm:gap-4 pb-4">
            <div className="flex items-center gap-2 min-w-0">
              <h1 className="text-xl sm:text-2xl font-bold tracking-tight truncate">{brief.title}</h1>
              <StatusBadge status={brief.status} size="sm" />
            </div>

            <div className="flex items-center gap-2 sm:gap-4">
              <TabsList className="h-9">
                <TabsTrigger value="feed" className="text-xs sm:text-sm">Feed</TabsTrigger>
                <TabsTrigger value="reports" className="text-xs sm:text-sm">Reports</TabsTrigger>
                <TabsTrigger value="sources" className="text-xs sm:text-sm">Sources</TabsTrigger>
              </TabsList>

              <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button variant="ghost" size="icon" className="h-8 w-8">
                  <MoreVertical className="h-5 w-5" />
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end">
                <DropdownMenuItem onClick={() => setIsEditing(true)}>
                  <Pencil className="mr-2 h-4 w-4" />
                  Edit Brief
                </DropdownMenuItem>
                <DropdownMenuItem
                  onClick={handleExecute}
                  disabled={isExecuting || brief.status !== 'ACTIVE'}
                >
                  <RefreshCw className={`mr-2 h-4 w-4 ${isExecuting ? 'animate-spin' : ''}`} />
                  {isExecuting ? 'Executing...' : 'Execute Brief'}
                </DropdownMenuItem>
                <DropdownMenuSeparator />
                <DropdownMenuItem
                  className="text-destructive focus:text-destructive"
                  onClick={() => setShowDeleteDialog(true)}
                >
                  <Trash2 className="mr-2 h-4 w-4" />
                  Delete Brief
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
            </div>
          </div>

          {/* Feed Tab */}
          <TabsContent value="feed" className="mt-4">
            <BriefFeedList briefingId={id!} />
          </TabsContent>

          {/* Reports Tab */}
          <TabsContent value="reports" className="mt-4">
            {isLoadingReports ? (
              <div className="space-y-4">
                {[...Array(3)].map((_, i) => (
                  <Skeleton key={i} className="h-20" />
                ))}
              </div>
            ) : reports.length === 0 ? (
              <EmptyState
                icon={FileText}
                title="No reports yet"
                description="Reports will appear here after the brief is executed."
                action={
                  brief.status === 'ACTIVE'
                    ? {
                        label: 'Execute Now',
                        onClick: handleExecute,
                      }
                    : undefined
                }
              />
            ) : (
              <>
                <div className="space-y-3">
                  {reports.map((report) => (
                    <Card
                      key={report.id}
                      className="cursor-pointer transition-colors hover:bg-muted/50"
                      onClick={() => navigate(`/briefs/${id}/reports/${report.id}`)}
                    >
                      <CardContent className="p-4">
                        <div className="flex items-center justify-between">
                          <div className="flex items-center gap-3">
                            <FileText className="h-5 w-5 text-muted-foreground" />
                            <div>
                              <div className="flex items-center gap-2">
                                <span className="font-medium">
                                  {format(new Date(report.generatedAt), 'EEEE, MMMM d, yyyy')}
                                </span>
                                <StatusBadge status={report.status} size="sm" />
                              </div>
                              <div className="flex items-center gap-4 text-sm text-muted-foreground mt-0.5">
                                <Tooltip>
                                  <TooltipTrigger asChild>
                                    <span className="flex items-center gap-1 cursor-default">
                                      <Calendar className="h-3 w-3" />
                                      {formatDistanceToNow(parseApiDate(report.generatedAt), { addSuffix: true })}
                                    </span>
                                  </TooltipTrigger>
                                  <TooltipContent>
                                    {format(parseApiDate(report.generatedAt), 'PPpp')}
                                  </TooltipContent>
                                </Tooltip>
                                <span className="flex items-center gap-1">
                                  <ListOrdered className="h-3 w-3" />
                                  {report.itemCount} items
                                </span>
                              </div>
                            </div>
                          </div>
                        </div>
                      </CardContent>
                    </Card>
                  ))}
                </div>
                {reportsTotalPages > 1 && (
                  <PaginationControls
                    currentPage={reportsPage}
                    totalPages={reportsTotalPages}
                    onPageChange={fetchReports}
                    disabled={isLoadingReports}
                  />
                )}
              </>
            )}
          </TabsContent>

          {/* Sources Tab */}
          <TabsContent value="sources" className="mt-4">
            <div className="flex items-center justify-between mb-4">
              <p className="text-sm text-muted-foreground">
                Manage RSS feeds for this brief
              </p>
              <Button size="sm" onClick={() => setShowAddSourceDialog(true)}>
                <Plus className="mr-2 h-4 w-4" />
                Add Source
              </Button>
            </div>
            {sources.length === 0 ? (
              <EmptyState
                icon={Rss}
                title="No sources yet"
                description="Add RSS feeds to start collecting news for this brief."
                action={{
                  label: 'Add Source',
                  onClick: () => setShowAddSourceDialog(true),
                }}
              />
            ) : (
              <div className="space-y-2">
                {sources.map((source) => (
                  <BriefSourceItem
                    key={source.id}
                    source={source}
                    onDelete={() => setSourceToDelete(source)}
                    onView={() => navigate(`/sources/${source.id}`)}
                  />
                ))}
              </div>
            )}
          </TabsContent>
        </Tabs>
      </main>

      <ConfirmDialog
        open={showDeleteDialog}
        onOpenChange={setShowDeleteDialog}
        title="Delete Brief"
        description={`Are you sure you want to delete "${brief.title}"? This action cannot be undone.`}
        confirmLabel="Delete"
        variant="destructive"
        onConfirm={handleDelete}
        isLoading={isDeleting}
      />

      <AddSourceDialog
        open={showAddSourceDialog}
        onOpenChange={setShowAddSourceDialog}
        briefingId={id}
        onSuccess={handleAddSource}
      />

      <ConfirmDialog
        open={!!sourceToDelete}
        onOpenChange={(open) => !open && setSourceToDelete(null)}
        title="Remove Source"
        description={`Are you sure you want to remove "${sourceToDelete?.name}" from this brief?`}
        confirmLabel="Remove"
        variant="destructive"
        onConfirm={() => sourceToDelete && handleDeleteSource(sourceToDelete.id)}
      />
    </>
  );
}

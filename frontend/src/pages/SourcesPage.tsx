import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { PageHeader } from '@/components/layout/PageHeader';
import { SourceCard } from '@/components/sources/SourceCard';
import { AddSourceDialog } from '@/components/sources/AddSourceDialog';
import { EmptyState } from '@/components/shared/EmptyState';
import { PaginationControls } from '@/components/shared/PaginationControls';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { Plus, Rss, Search } from 'lucide-react';
import { apiClient } from '@/lib/api';
import { toast } from '@/hooks/use-toast';
import type { SourceDTO, SourceStatus } from '@/types';

export default function SourcesPage() {
  const navigate = useNavigate();
  const [sources, setSources] = useState<SourceDTO[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [currentPage, setCurrentPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [statusFilter, setStatusFilter] = useState<SourceStatus | 'ALL'>('ALL');
  const [searchQuery, setSearchQuery] = useState('');
  const [showAddDialog, setShowAddDialog] = useState(false);

  const fetchSources = useCallback(async (page = 0) => {
    setIsLoading(true);
    try {
      const status = statusFilter === 'ALL' ? undefined : statusFilter;
      const response = await apiClient.getSources(page, 100, status);
      setSources(response.content);
      setTotalPages(response.totalPages);
      setCurrentPage(response.number);
    } catch (error: unknown) {
      toast({
        title: 'Failed to load sources',
        description: error instanceof Error ? error.message : 'An error occurred',
        variant: 'destructive',
      });
    } finally {
      setIsLoading(false);
    }
  }, [statusFilter]);

  useEffect(() => {
    fetchSources(0);
  }, [fetchSources]);

  // Client-side filtering by search query
  const filteredSources = sources.filter((source) => {
    if (!searchQuery.trim()) return true;
    const query = searchQuery.toLowerCase();
    return (
      source.name.toLowerCase().includes(query) ||
      source.url.toLowerCase().includes(query) ||
      source.tags?.some((tag) => tag.toLowerCase().includes(query))
    );
  });

  const handleSourceClick = (source: SourceDTO) => {
    navigate(`/sources/${source.id}`);
  };

  const handleAddSuccess = () => {
    fetchSources(0);
  };

  return (
    <>
      <PageHeader title="Sources">
        <Button onClick={() => setShowAddDialog(true)}>
          <Plus className="mr-2 h-4 w-4" />
          Add Source
        </Button>
      </PageHeader>

      <main className="flex-1 p-4">
        {/* Filter Bar */}
        <div className="flex flex-col sm:flex-row gap-4 mb-6">
          <div className="relative flex-1">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
            <Input
              placeholder="Search sources..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="pl-9"
            />
          </div>
          <Select
            value={statusFilter}
            onValueChange={(value) => setStatusFilter(value as SourceStatus | 'ALL')}
          >
            <SelectTrigger className="w-[150px]">
              <SelectValue placeholder="Status" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ALL">All Status</SelectItem>
              <SelectItem value="ACTIVE">Active</SelectItem>
              <SelectItem value="PAUSED">Paused</SelectItem>
              <SelectItem value="ERROR">Error</SelectItem>
            </SelectContent>
          </Select>
        </div>

        {/* Content */}
        {isLoading ? (
          <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
            {[...Array(6)].map((_, i) => (
              <Skeleton key={i} className="h-32" />
            ))}
          </div>
        ) : filteredSources.length === 0 ? (
          <EmptyState
            icon={Rss}
            title={searchQuery ? 'No matching sources' : 'No sources yet'}
            description={
              searchQuery
                ? 'Try a different search term.'
                : 'Add your first RSS feed to start aggregating news content.'
            }
            action={
              searchQuery
                ? undefined
                : {
                    label: 'Add Source',
                    onClick: () => setShowAddDialog(true),
                  }
            }
          />
        ) : (
          <>
            <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
              {filteredSources.map((source) => (
                <SourceCard
                  key={source.id}
                  source={source}
                  onClick={() => handleSourceClick(source)}
                />
              ))}
            </div>
            <PaginationControls
              currentPage={currentPage}
              totalPages={totalPages}
              onPageChange={fetchSources}
              disabled={isLoading}
            />
          </>
        )}
      </main>

      <AddSourceDialog
        open={showAddDialog}
        onOpenChange={setShowAddDialog}
        onSuccess={handleAddSuccess}
      />
    </>
  );
}

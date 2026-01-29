import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Loader2, Rss, Mail, Globe, MessageSquare } from 'lucide-react';
import { Textarea } from '@/components/ui/textarea';
import { toast } from '@/hooks/use-toast';
import { apiClient } from '@/lib/api';
import { z } from 'zod';
import type { SourceDTO, SourceType } from '@/types';

interface AddSourceDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  briefingId?: string;
  onSuccess?: (source: SourceDTO) => void;
}

const rssSchema = z.object({
  url: z.string().url('Please enter a valid URL'),
  name: z.string().optional(),
});

const emailSchema = z.object({
  name: z.string().min(1, 'Name is required for email sources'),
});

const webSchema = z.object({
  url: z.string().url('Please enter a valid URL'),
  extractionPrompt: z.string().min(1, 'Extraction prompt is required'),
  name: z.string().optional(),
});

const redditSchema = z.object({
  subreddit: z.string()
    .min(2, 'Subreddit name must be at least 2 characters')
    .max(21, 'Subreddit name must be at most 21 characters')
    .regex(/^[A-Za-z0-9_]+$/, 'Subreddit name can only contain letters, numbers, and underscores'),
  name: z.string().optional(),
});

export function AddSourceDialog({ open, onOpenChange, briefingId, onSuccess }: AddSourceDialogProps) {
  const navigate = useNavigate();
  const [isLoading, setIsLoading] = useState(false);
  const [sourceType, setSourceType] = useState<SourceType>('RSS');
  const [formData, setFormData] = useState({
    url: '',
    name: '',
    tags: '',
    extractionPrompt: '',
    subreddit: '',
  });
  const [errors, setErrors] = useState<Record<string, string>>({});

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrors({});

    try {
      // Validate based on source type
      if (sourceType === 'RSS') {
        rssSchema.parse({
          url: formData.url,
          name: formData.name || undefined,
        });
      } else if (sourceType === 'WEB') {
        webSchema.parse({
          url: formData.url,
          extractionPrompt: formData.extractionPrompt,
          name: formData.name || undefined,
        });
      } else if (sourceType === 'REDDIT') {
        redditSchema.parse({
          subreddit: formData.subreddit,
          name: formData.name || undefined,
        });
      } else {
        emailSchema.parse({
          name: formData.name,
        });
      }

      setIsLoading(true);

      const tagsArray = formData.tags
        .split(',')
        .map((t) => t.trim())
        .filter((t) => t.length > 0);

      const sourceData = {
        url: sourceType === 'RSS' || sourceType === 'WEB'
          ? formData.url
          : sourceType === 'REDDIT'
            ? formData.subreddit
            : undefined,
        name: formData.name || undefined,
        type: sourceType,
        tags: tagsArray.length > 0 ? tagsArray : undefined,
        extractionPrompt: sourceType === 'WEB' ? formData.extractionPrompt : undefined,
      };

      const source = briefingId
        ? await apiClient.createBriefingSource(briefingId, sourceData)
        : await apiClient.createSource(sourceData);

      toast({
        title: 'Source added',
        description: `Successfully added "${source.name}"`,
      });

      setFormData({ url: '', name: '', tags: '', extractionPrompt: '', subreddit: '' });
      setSourceType('RSS');
      onOpenChange(false);

      // Redirect to source detail page
      navigate(`/sources/${source.id}`);
    } catch (error: unknown) {
      if (error instanceof z.ZodError) {
        const fieldErrors: Record<string, string> = {};
        error.errors.forEach((err) => {
          if (err.path[0]) {
            fieldErrors[err.path[0].toString()] = err.message;
          }
        });
        setErrors(fieldErrors);
      } else {
        toast({
          title: 'Failed to add source',
          description: error instanceof Error ? error.message : 'An error occurred',
          variant: 'destructive',
        });
      }
    } finally {
      setIsLoading(false);
    }
  };

  const handleClose = () => {
    setFormData({ url: '', name: '', tags: '', extractionPrompt: '', subreddit: '' });
    setSourceType('RSS');
    setErrors({});
    onOpenChange(false);
  };

  return (
    <Dialog open={open} onOpenChange={handleClose}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Add Source</DialogTitle>
          <DialogDescription>
            {sourceType === 'RSS'
              ? 'Add a new RSS feed to your sources.'
              : sourceType === 'WEB'
              ? 'Monitor a web page and extract news items using AI.'
              : sourceType === 'REDDIT'
              ? 'Follow a subreddit and get links from hot posts.'
              : 'Create an email source to receive newsletters.'}
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={handleSubmit}>
          <div className="space-y-4 py-4">
            <div className="space-y-2">
              <Label>Source Type</Label>
              <div className="grid grid-cols-2 gap-2">
                <Button
                  type="button"
                  variant={sourceType === 'RSS' ? 'default' : 'outline'}
                  onClick={() => setSourceType('RSS')}
                  disabled={isLoading}
                >
                  <Rss className="mr-2 h-4 w-4" />
                  RSS Feed
                </Button>
                <Button
                  type="button"
                  variant={sourceType === 'EMAIL' ? 'default' : 'outline'}
                  onClick={() => setSourceType('EMAIL')}
                  disabled={isLoading}
                >
                  <Mail className="mr-2 h-4 w-4" />
                  Email
                </Button>
                <Button
                  type="button"
                  variant={sourceType === 'WEB' ? 'default' : 'outline'}
                  onClick={() => setSourceType('WEB')}
                  disabled={isLoading}
                >
                  <Globe className="mr-2 h-4 w-4" />
                  Web Page
                </Button>
                <Button
                  type="button"
                  variant={sourceType === 'REDDIT' ? 'default' : 'outline'}
                  onClick={() => setSourceType('REDDIT')}
                  disabled={isLoading}
                >
                  <MessageSquare className="mr-2 h-4 w-4" />
                  Reddit
                </Button>
              </div>
            </div>

            {(sourceType === 'RSS' || sourceType === 'WEB') && (
              <div className="space-y-2">
                <Label htmlFor="url">{sourceType === 'RSS' ? 'Feed URL' : 'Page URL'} *</Label>
                <Input
                  id="url"
                  type="url"
                  placeholder={sourceType === 'RSS' ? 'https://example.com/feed.xml' : 'https://news.ycombinator.com'}
                  value={formData.url}
                  onChange={(e) => setFormData({ ...formData, url: e.target.value })}
                  disabled={isLoading}
                  className={errors.url ? 'border-destructive' : ''}
                />
                {errors.url && (
                  <p className="text-sm text-destructive">{errors.url}</p>
                )}
              </div>
            )}

            {sourceType === 'REDDIT' && (
              <div className="space-y-2">
                <Label htmlFor="subreddit">Subreddit *</Label>
                <div className="flex items-center gap-2">
                  <span className="text-muted-foreground">r/</span>
                  <Input
                    id="subreddit"
                    placeholder="programming"
                    value={formData.subreddit}
                    onChange={(e) => setFormData({ ...formData, subreddit: e.target.value })}
                    disabled={isLoading}
                    className={errors.subreddit ? 'border-destructive' : ''}
                  />
                </div>
                {errors.subreddit && (
                  <p className="text-sm text-destructive">{errors.subreddit}</p>
                )}
                <p className="text-xs text-muted-foreground">
                  Only external link posts will be fetched (no images, videos, or text posts)
                </p>
              </div>
            )}

            {sourceType === 'WEB' && (
              <div className="space-y-2">
                <Label htmlFor="extractionPrompt">Extraction Prompt *</Label>
                <Textarea
                  id="extractionPrompt"
                  placeholder="Extract the top stories about AI and machine learning. For each item, provide the title, a brief summary, and the link."
                  value={formData.extractionPrompt}
                  onChange={(e) => setFormData({ ...formData, extractionPrompt: e.target.value })}
                  disabled={isLoading}
                  className={errors.extractionPrompt ? 'border-destructive' : ''}
                  rows={3}
                />
                {errors.extractionPrompt && (
                  <p className="text-sm text-destructive">{errors.extractionPrompt}</p>
                )}
                <p className="text-xs text-muted-foreground">
                  Describe what items to extract from the page
                </p>
              </div>
            )}

            <div className="space-y-2">
              <Label htmlFor="name">
                Name {sourceType === 'EMAIL' ? '*' : '(optional)'}
              </Label>
              <Input
                id="name"
                placeholder={sourceType === 'EMAIL' ? 'My Newsletter' : 'Custom name for the source'}
                value={formData.name}
                onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                disabled={isLoading}
                className={errors.name ? 'border-destructive' : ''}
              />
              {errors.name && (
                <p className="text-sm text-destructive">{errors.name}</p>
              )}
              {(sourceType === 'RSS' || sourceType === 'WEB') && (
                <p className="text-xs text-muted-foreground">
                  Leave empty to auto-detect from {sourceType === 'RSS' ? 'feed' : 'page'}
                </p>
              )}
            </div>

            {sourceType === 'EMAIL' && (
              <div className="rounded-md border border-muted bg-muted/50 p-3">
                <p className="text-sm text-muted-foreground">
                  A unique email address will be generated for this source.
                  Forward newsletters to this address to have them processed.
                </p>
              </div>
            )}

            <div className="space-y-2">
              <Label htmlFor="tags">Tags (optional)</Label>
              <Input
                id="tags"
                placeholder="tech, news, ai"
                value={formData.tags}
                onChange={(e) => setFormData({ ...formData, tags: e.target.value })}
                disabled={isLoading}
              />
              <p className="text-xs text-muted-foreground">
                Comma-separated list of tags
              </p>
            </div>
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={handleClose} disabled={isLoading}>
              Cancel
            </Button>
            <Button type="submit" disabled={isLoading}>
              {isLoading && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
              Add Source
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

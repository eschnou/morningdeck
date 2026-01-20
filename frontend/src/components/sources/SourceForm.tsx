import { useState, useEffect } from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { Loader2 } from 'lucide-react';
import { z } from 'zod';
import type { SourceDTO, UpdateSourceRequest } from '@/types';

interface SourceFormProps {
  source: SourceDTO;
  onSubmit: (data: UpdateSourceRequest) => void;
  onCancel: () => void;
  isLoading?: boolean;
}

const formSchema = z.object({
  name: z.string().min(1, 'Name is required').max(255),
  status: z.enum(['ACTIVE', 'PAUSED']),
});

export function SourceForm({ source, onSubmit, onCancel, isLoading = false }: SourceFormProps) {
  const [formData, setFormData] = useState({
    name: source.name,
    tags: source.tags.join(', '),
    status: source.status as 'ACTIVE' | 'PAUSED',
    extractionPrompt: source.extractionPrompt || '',
  });
  const [errors, setErrors] = useState<Record<string, string>>({});

  useEffect(() => {
    setFormData({
      name: source.name,
      tags: source.tags.join(', '),
      status: source.status as 'ACTIVE' | 'PAUSED',
      extractionPrompt: source.extractionPrompt || '',
    });
  }, [source]);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setErrors({});

    try {
      formSchema.parse({
        name: formData.name,
        status: formData.status,
      });

      const tagsArray = formData.tags
        .split(',')
        .map((t) => t.trim())
        .filter((t) => t.length > 0);

      onSubmit({
        name: formData.name,
        tags: tagsArray,
        status: formData.status,
        extractionPrompt: source.type === 'WEB' ? formData.extractionPrompt : undefined,
      });
    } catch (error: unknown) {
      if (error instanceof z.ZodError) {
        const fieldErrors: Record<string, string> = {};
        error.errors.forEach((err) => {
          if (err.path[0]) {
            fieldErrors[err.path[0].toString()] = err.message;
          }
        });
        setErrors(fieldErrors);
      }
    }
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <div className="space-y-2">
        <Label htmlFor="name">Name</Label>
        <Input
          id="name"
          value={formData.name}
          onChange={(e) => setFormData({ ...formData, name: e.target.value })}
          disabled={isLoading}
          className={errors.name ? 'border-destructive' : ''}
        />
        {errors.name && (
          <p className="text-sm text-destructive">{errors.name}</p>
        )}
      </div>

      <div className="space-y-2">
        <Label htmlFor="tags">Tags</Label>
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

      <div className="space-y-2">
        <Label htmlFor="status">Status</Label>
        <Select
          value={formData.status}
          onValueChange={(value: 'ACTIVE' | 'PAUSED') =>
            setFormData({ ...formData, status: value })
          }
          disabled={isLoading}
        >
          <SelectTrigger>
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="ACTIVE">Active</SelectItem>
            <SelectItem value="PAUSED">Paused</SelectItem>
          </SelectContent>
        </Select>
      </div>

      {source.type === 'WEB' && (
        <div className="space-y-2">
          <Label htmlFor="extractionPrompt">Extraction Prompt</Label>
          <Textarea
            id="extractionPrompt"
            value={formData.extractionPrompt}
            onChange={(e) => setFormData({ ...formData, extractionPrompt: e.target.value })}
            disabled={isLoading}
            rows={3}
          />
          <p className="text-xs text-muted-foreground">
            Describe what items to extract from the page
          </p>
        </div>
      )}

      <div className="flex justify-end gap-2 pt-4">
        <Button type="button" variant="outline" onClick={onCancel} disabled={isLoading}>
          Cancel
        </Button>
        <Button type="submit" disabled={isLoading}>
          {isLoading && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
          Save Changes
        </Button>
      </div>
    </form>
  );
}

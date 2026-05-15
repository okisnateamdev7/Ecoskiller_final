import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:lucide_icons/lucide_icons.dart';
import 'package:animate_do/animate_do.dart';
import 'package:ecoskiller_mobile_app/core/theme/app_theme.dart';
import '../../data/models/idea_submission.dart';
import '../../data/services/shark_tank_service.dart';

class IdeaSubmissionWizard extends StatefulWidget {
  final Map<String, dynamic> user;
  const IdeaSubmissionWizard({super.key, required this.user});

  @override
  State<IdeaSubmissionWizard> createState() => _IdeaSubmissionWizardState();
}

class _IdeaSubmissionWizardState extends State<IdeaSubmissionWizard> {
  final PageController _pageController = PageController();
  int _currentStep = 0;
  final int _totalSteps = 5;

  // Form Controllers
  final TextEditingController _titleController = TextEditingController();
  final TextEditingController _problemController = TextEditingController();
  final TextEditingController _solutionController = TextEditingController();
  final TextEditingController _audienceController = TextEditingController();
  final TextEditingController _revenueController = TextEditingController();
  final TextEditingController _prototypeController = TextEditingController();
  final TextEditingController _mentorshipController = TextEditingController();

  bool _isAnalyzing = false;
  Map<String, dynamic>? _aiFeedback;

  @override
  Widget build(BuildContext context) {
    bool isDark = Theme.of(context).brightness == Brightness.dark;

    return Scaffold(
      backgroundColor: isDark ? AppTheme.midnightAbyss : AppTheme.skyWhite,
      appBar: AppBar(
        title: Text('IDEA WIZARD', style: GoogleFonts.inter(fontWeight: FontWeight.w900, letterSpacing: 2)),
        backgroundColor: Colors.transparent,
        elevation: 0,
        actions: [
          Center(
            child: Padding(
              padding: const EdgeInsets.only(right: 20),
              child: Text(
                'Step ${_currentStep + 1} of $_totalSteps',
                style: GoogleFonts.inter(color: Colors.grey, fontWeight: FontWeight.bold),
              ),
            ),
          ),
        ],
      ),
      body: Column(
        children: [
          LinearProgressIndicator(
            value: (_currentStep + 1) / _totalSteps,
            backgroundColor: Colors.grey[300],
            valueColor: const AlwaysStoppedAnimation<Color>(AppTheme.linkedinBlue),
          ),
          Expanded(
            child: PageView(
              controller: _pageController,
              physics: const NeverScrollableScrollPhysics(),
              children: [
                _buildStepBasics(),
                _buildStepSolution(),
                _buildStepModel(),
                _buildStepAiReview(),
                _buildStepFinal(),
              ],
            ),
          ),
          _buildNavigationButtons(),
        ],
      ),
    );
  }

  Widget _buildStepBasics() {
    return _buildStepContainer(
      title: 'The Foundation',
      subtitle: 'What is your idea called and what problem does it solve?',
      children: [
        _buildTextField('Idea Title', _titleController, 'e.g. EcoSmart Bin'),
        const SizedBox(height: 24),
        _buildTextField('Problem Statement', _problemController, 'Describe the pain point...', maxLines: 5),
      ],
    );
  }

  Widget _buildStepSolution() {
    return _buildStepContainer(
      title: 'The Innovation',
      subtitle: 'How do you plan to solve the problem?',
      children: [
        _buildTextField('Your Solution', _solutionController, 'Describe your product...', maxLines: 5),
        const SizedBox(height: 24),
        _buildTextField('Target Audience', _audienceController, 'Who is this for?'),
      ],
    );
  }

  Widget _buildStepModel() {
    return _buildStepContainer(
      title: 'Business & Team',
      subtitle: 'How will it sustain and who is with you?',
      children: [
        _buildTextField('Revenue / Impact Model', _revenueController, 'How will it make money or impact?', maxLines: 3),
        const SizedBox(height: 24),
        _buildTextField('Mentorship Needed', _mentorshipController, 'e.g. Tech, Marketing, Finance'),
        const SizedBox(height: 24),
        _buildTextField('Prototype Link (Optional)', _prototypeController, 'URL to demo...'),
      ],
    );
  }

  Widget _buildStepAiReview() {
    return _buildStepContainer(
      title: 'AI Analysis',
      subtitle: 'Get instant feedback on your pitch before submitting.',
      children: [
        if (_aiFeedback == null && !_isAnalyzing)
          Center(
            child: Column(
              children: [
                const SizedBox(height: 40),
                const Icon(LucideIcons.zap, size: 64, color: Colors.amber),
                const SizedBox(height: 24),
                Text(
                  'Let AI evaluate your pitch',
                  style: GoogleFonts.inter(fontWeight: FontWeight.bold),
                ),
                const SizedBox(height: 16),
                ElevatedButton(
                  onPressed: _runAiAnalysis,
                  style: ElevatedButton.styleFrom(backgroundColor: Colors.amber, foregroundColor: Colors.black),
                  child: const Text('RUN AI REVIEW'),
                ),
              ],
            ),
          )
        else if (_isAnalyzing)
          const Center(
            child: Column(
              children: [
                SizedBox(height: 40),
                CircularProgressIndicator(color: Colors.amber),
                SizedBox(height: 24),
                Text('AI is analyzing your idea...'),
              ],
            ),
          )
        else
          _buildAiFeedbackPanel(),
      ],
    );
  }

  Widget _buildAiFeedbackPanel() {
    return FadeIn(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceAround,
            children: [
              _buildScoreCircle('Clarity', _aiFeedback!['clarityScore'], Colors.blue),
              _buildScoreCircle('Market', _aiFeedback!['marketFeasibility'], Colors.green),
              _buildScoreCircle('Innovation', _aiFeedback!['innovationScore'], Colors.purple),
            ],
          ),
          const SizedBox(height: 32),
          Text('SUGGESTIONS', style: GoogleFonts.inter(fontWeight: FontWeight.w900, fontSize: 12, letterSpacing: 1.5)),
          const SizedBox(height: 12),
          ...(_aiFeedback!['suggestions'] as List).map((s) => Padding(
                padding: const EdgeInsets.only(bottom: 8.0),
                child: Row(
                  children: [
                    const Icon(LucideIcons.checkCircle, size: 16, color: Colors.green),
                    const SizedBox(width: 12),
                    Expanded(child: Text(s, style: GoogleFonts.inter(fontSize: 13))),
                  ],
                ),
              )),
          const SizedBox(height: 24),
          TextButton.icon(
            onPressed: () => setState(() => _aiFeedback = null),
            icon: const Icon(LucideIcons.refreshCw, size: 16),
            label: const Text('Re-evaluate'),
          ),
        ],
      ),
    );
  }

  Widget _buildStepFinal() {
    return _buildStepContainer(
      title: 'Ready to Pitch?',
      subtitle: 'Upload a short video and submit your final idea.',
      children: [
        Container(
          height: 150,
          width: double.infinity,
          decoration: BoxDecoration(
            color: AppTheme.linkedinBlue.withOpacity(0.05),
            borderRadius: BorderRadius.circular(16),
            border: Border.all(color: AppTheme.linkedinBlue.withOpacity(0.2), style: BorderStyle.solid),
          ),
          child: const Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(LucideIcons.video, size: 40, color: AppTheme.linkedinBlue),
              SizedBox(height: 12),
              Text('UPLOAD VIDEO PITCH', style: TextStyle(fontWeight: FontWeight.bold)),
              Text('(Max 5 minutes)', style: TextStyle(fontSize: 10, color: Colors.grey)),
            ],
          ),
        ),
        const SizedBox(height: 32),
        const Row(
          children: [
            Icon(LucideIcons.shieldCheck, color: Colors.green, size: 20),
            SizedBox(width: 12),
            Expanded(
              child: Text(
                'By submitting, you agree to Shark Tank terms and intellectual property policies.',
                style: TextStyle(fontSize: 11, color: Colors.grey),
              ),
            ),
          ],
        ),
      ],
    );
  }

  Widget _buildStepContainer({required String title, required String subtitle, required List<Widget> children}) {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          FadeInDown(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(title, style: GoogleFonts.inter(fontSize: 28, fontWeight: FontWeight.w900)),
                const SizedBox(height: 8),
                Text(subtitle, style: GoogleFonts.inter(color: Colors.grey, fontSize: 14)),
                const SizedBox(height: 32),
              ],
            ),
          ),
          ...children,
        ],
      ),
    );
  }

  Widget _buildTextField(String label, TextEditingController controller, String hint, {int maxLines = 1}) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(label.toUpperCase(), style: GoogleFonts.inter(fontSize: 11, fontWeight: FontWeight.w900, letterSpacing: 1.5)),
        const SizedBox(height: 8),
        TextField(
          controller: controller,
          maxLines: maxLines,
          decoration: InputDecoration(
            hintText: hint,
            filled: true,
            fillColor: Colors.grey.withOpacity(0.05),
            border: OutlineInputBorder(borderRadius: BorderRadius.circular(12), borderSide: BorderSide.none),
          ),
        ),
      ],
    );
  }

  Widget _buildScoreCircle(String label, int score, Color color) {
    return Column(
      children: [
        Stack(
          alignment: Alignment.center,
          children: [
            SizedBox(
              height: 60,
              width: 60,
              child: CircularProgressIndicator(
                value: score / 100,
                color: color,
                strokeWidth: 6,
                backgroundColor: color.withOpacity(0.1),
              ),
            ),
            Text('$score', style: GoogleFonts.inter(fontWeight: FontWeight.bold, fontSize: 18)),
          ],
        ),
        const SizedBox(height: 8),
        Text(label, style: GoogleFonts.inter(fontSize: 10, color: Colors.grey, fontWeight: FontWeight.bold)),
      ],
    );
  }

  Widget _buildNavigationButtons() {
    return Padding(
      padding: const EdgeInsets.all(20.0),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          if (_currentStep > 0)
            TextButton(
              onPressed: () {
                _pageController.previousPage(duration: const Duration(milliseconds: 300), curve: Curves.easeInOut);
                setState(() => _currentStep--);
              },
              child: const Text('BACK'),
            )
          else
            const SizedBox(),
          ElevatedButton(
            onPressed: _currentStep == _totalSteps - 1 ? _submitFinal : _nextStep,
            style: ElevatedButton.styleFrom(
              backgroundColor: AppTheme.linkedinBlue,
              padding: const EdgeInsets.symmetric(horizontal: 40, vertical: 16),
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
            ),
            child: Text(_currentStep == _totalSteps - 1 ? 'SUBMIT IDEA' : 'NEXT'),
          ),
        ],
      ),
    );
  }

  void _nextStep() {
    _pageController.nextPage(duration: const Duration(milliseconds: 300), curve: Curves.easeInOut);
    setState(() => _currentStep++);
  }

  void _runAiAnalysis() async {
    setState(() => _isAnalyzing = true);
    final feedback = await SharkTankService().getAiFeedback(_problemController.text + _solutionController.text);
    setState(() {
      _aiFeedback = feedback;
      _isAnalyzing = false;
    });
  }

  void _submitFinal() async {
    final submission = IdeaSubmission(
      title: _titleController.text,
      problemStatement: _problemController.text,
      solution: _solutionController.text,
      targetAudience: _audienceController.text,
      revenueModel: _revenueController.text,
      prototypeLink: _prototypeController.text,
      teamMembers: [], // Simplified for now
      mentorshipDomain: _mentorshipController.text,
      status: 'SUBMITTED',
    );

    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (context) => const Center(child: CircularProgressIndicator()),
    );

    final result = await SharkTankService().submitIdea(submission);
    
    if (mounted) {
      Navigator.pop(context); // Pop loading
      if (result['status'] == 'success') {
        _showSuccessDialog();
      } else {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(result['message'])));
      }
    }
  }

  void _showSuccessDialog() {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
        title: const Icon(LucideIcons.partyPopper, color: Colors.green, size: 64),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text('Idea Submitted!', style: GoogleFonts.inter(fontWeight: FontWeight.bold, fontSize: 20)),
            const SizedBox(height: 12),
            const Text('Your project is now under review by our experts. Good luck!', textAlign: TextAlign.center),
          ],
        ),
        actions: [
          Center(
            child: ElevatedButton(
              onPressed: () {
                Navigator.pop(context); // Pop dialog
                Navigator.pop(context); // Pop wizard
              },
              child: const Text('AWESOME'),
            ),
          ),
        ],
      ),
    );
  }
}
